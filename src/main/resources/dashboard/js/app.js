/**
 * SMPStats Dashboard - Main Application JavaScript
 */

class SMPStatsDashboard {
    constructor() {
        this.config = null;
        this.isAdmin = false;
        this.refreshInterval = null;
        
        this.init();
    }
    
    async init() {
        await this.loadConfig();
        await this.checkAuth();
        this.setupEventListeners();
        this.setupNavigation();
        this.loadInitialData();
        this.startAutoRefresh();
    }
    
    // ============== Configuration ==============
    
    async loadConfig() {
        try {
            const response = await fetch('/api/public/config');
            this.config = await response.json();
            this.applyConfig();
        } catch (error) {
            console.error('Failed to load config:', error);
            this.config = {
                publicEnabled: true,
                showOnlinePlayers: true,
                showLeaderboards: true,
                showRecentMoments: true,
                showServerStats: true,
                adminEnabled: true
            };
        }
    }
    
    applyConfig() {
        // Show/hide features based on config
        if (!this.config.showLeaderboards) {
            document.querySelector('[data-section="leaderboard"]')?.classList.add('hidden');
        }
        if (!this.config.showRecentMoments) {
            document.querySelector('[data-section="moments"]')?.classList.add('hidden');
        }
        if (!this.config.adminEnabled) {
            document.getElementById('admin-login-btn')?.classList.add('hidden');
        }
    }
    
    // ============== Authentication ==============
    
    async checkAuth() {
        try {
            const response = await fetch('/api/admin/check');
            const data = await response.json();
            this.isAdmin = data.authenticated;
            this.updateAuthUI();
        } catch (error) {
            console.error('Auth check failed:', error);
            this.isAdmin = false;
        }
    }
    
    updateAuthUI() {
        const loginBtn = document.getElementById('admin-login-btn');
        const logoutBtn = document.getElementById('admin-logout-btn');
        const adminNav = document.querySelector('[data-section="admin"]');
        const adminSection = document.getElementById('admin-section');
        
        if (this.isAdmin) {
            loginBtn?.classList.add('hidden');
            logoutBtn?.classList.remove('hidden');
            adminNav?.classList.remove('hidden');
            adminSection?.classList.remove('hidden');
        } else {
            loginBtn?.classList.remove('hidden');
            logoutBtn?.classList.add('hidden');
            adminNav?.classList.add('hidden');
            adminSection?.classList.add('hidden');
        }
    }
    
    async login(password) {
        try {
            const response = await fetch('/api/admin/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ password })
            });
            
            if (response.ok) {
                this.isAdmin = true;
                this.updateAuthUI();
                this.hideLoginModal();
                this.loadAdminData();
                return { success: true };
            } else {
                const data = await response.json();
                return { success: false, error: data.error || 'Login failed' };
            }
        } catch (error) {
            return { success: false, error: 'Network error' };
        }
    }
    
    async logout() {
        try {
            await fetch('/api/admin/logout');
        } catch (error) {
            console.error('Logout failed:', error);
        }
        this.isAdmin = false;
        this.updateAuthUI();
        this.showSection('overview');
    }
    
    // ============== Navigation ==============
    
    setupNavigation() {
        document.querySelectorAll('.nav-link').forEach(link => {
            link.addEventListener('click', (e) => {
                e.preventDefault();
                const section = link.dataset.section;
                if (section === 'admin' && !this.isAdmin) return;
                this.showSection(section);
            });
        });
    }
    
    showSection(name) {
        // Update nav links
        document.querySelectorAll('.nav-link').forEach(link => {
            link.classList.toggle('active', link.dataset.section === name);
        });
        
        // Update sections
        document.querySelectorAll('.section').forEach(section => {
            section.classList.toggle('active', section.id === `${name}-section`);
        });
        
        // Load data for the section
        switch (name) {
            case 'overview':
                this.loadOverviewData();
                break;
            case 'leaderboard':
                this.loadLeaderboard();
                break;
            case 'moments':
                this.loadMoments();
                break;
            case 'admin':
                this.loadAdminData();
                break;
        }
    }
    
    // ============== Event Listeners ==============
    
    setupEventListeners() {
        // Login modal
        document.getElementById('admin-login-btn')?.addEventListener('click', () => this.showLoginModal());
        document.getElementById('cancel-login')?.addEventListener('click', () => this.hideLoginModal());
        document.getElementById('admin-logout-btn')?.addEventListener('click', () => this.logout());
        
        document.getElementById('login-form')?.addEventListener('submit', async (e) => {
            e.preventDefault();
            const password = document.getElementById('password').value;
            const result = await this.login(password);
            
            if (!result.success) {
                const errorEl = document.getElementById('login-error');
                errorEl.textContent = result.error;
                errorEl.classList.remove('hidden');
            }
        });
        
        // Leaderboard filters
        document.getElementById('leaderboard-days')?.addEventListener('change', () => this.loadLeaderboard());
        document.getElementById('leaderboard-limit')?.addEventListener('change', () => this.loadLeaderboard());
        
        // Moments filter
        document.getElementById('moments-limit')?.addEventListener('change', () => this.loadMoments());
        
        // Admin tabs
        document.querySelectorAll('.admin-tab').forEach(tab => {
            tab.addEventListener('click', () => {
                document.querySelectorAll('.admin-tab').forEach(t => t.classList.remove('active'));
                document.querySelectorAll('.admin-content').forEach(c => c.classList.remove('active'));
                
                tab.classList.add('active');
                document.getElementById(`admin-${tab.dataset.tab}`)?.classList.add('active');
                
                this.loadAdminTab(tab.dataset.tab);
            });
        });
        
        // Heatmap
        document.getElementById('load-heatmap')?.addEventListener('click', () => this.loadHeatmap());
        
        // Player search
        document.getElementById('player-search')?.addEventListener('input', (e) => {
            this.filterPlayers(e.target.value);
        });
        
        // Close modal on outside click
        document.getElementById('login-modal')?.addEventListener('click', (e) => {
            if (e.target.id === 'login-modal') {
                this.hideLoginModal();
            }
        });
        
        // Biomes list toggle
        document.getElementById('biomes-toggle')?.addEventListener('click', () => {
            this.toggleBiomesList();
        });
    }
    
    showLoginModal() {
        document.getElementById('login-modal')?.classList.remove('hidden');
        document.getElementById('password')?.focus();
        document.getElementById('login-error')?.classList.add('hidden');
    }
    
    hideLoginModal() {
        document.getElementById('login-modal')?.classList.add('hidden');
        document.getElementById('password').value = '';
    }
    
    // ============== Data Loading ==============
    
    loadInitialData() {
        this.loadOverviewData();
    }
    
    startAutoRefresh() {
        // Refresh data every 30 seconds
        this.refreshInterval = setInterval(() => {
            const activeSection = document.querySelector('.section.active');
            if (activeSection?.id === 'overview-section') {
                this.loadOnlinePlayers();
            }
        }, 30000);
    }
    
    async loadOverviewData() {
        await Promise.all([
            this.loadOnlinePlayers(),
            this.loadServerStats()
        ]);
    }
    
    async loadOnlinePlayers() {
        if (!this.config?.showOnlinePlayers) return;
        
        try {
            const response = await fetch('/api/public/online');
            const data = await response.json();
            
            document.getElementById('online-count').textContent = data.count;
            
            const listEl = document.getElementById('online-players-list');
            if (data.players.length === 0) {
                listEl.innerHTML = '<p class="placeholder">No players online</p>';
            } else {
                listEl.innerHTML = data.players
                    .map(name => `<span class="player-tag">${this.escapeHtml(name)}</span>`)
                    .join('');
            }
        } catch (error) {
            console.error('Failed to load online players:', error);
        }
    }
    
    async loadServerStats() {
        if (!this.config?.showServerStats) return;
        
        try {
            const response = await fetch('/api/public/stats');
            const data = await response.json();
            
            document.getElementById('total-playtime').textContent = this.formatHours(data.totalPlaytimeHours);
            document.getElementById('total-deaths').textContent = this.formatNumber(data.totalDeaths);
            document.getElementById('total-kills').textContent = this.formatNumber(data.totalPlayerKills + data.totalMobKills);
            document.getElementById('total-players').textContent = this.formatNumber(data.totalPlayers);
            document.getElementById('blocks-broken').textContent = this.formatNumber(data.totalBlocksBroken);
            document.getElementById('blocks-placed').textContent = this.formatNumber(data.totalBlocksPlaced);
            document.getElementById('distance-traveled').textContent = `${this.formatNumber(Math.round(data.totalDistanceKm))} km`;
            document.getElementById('biomes-discovered').textContent = this.formatNumber(data.uniqueBiomesDiscovered);
            
            // Update biomes list
            this.updateBiomesList(data.biomesList || []);
        } catch (error) {
            console.error('Failed to load server stats:', error);
        }
    }
    
    updateBiomesList(biomes) {
        const listEl = document.getElementById('biomes-list');
        if (!listEl) return;
        
        if (biomes.length === 0) {
            listEl.innerHTML = '<p class="placeholder">No biomes discovered yet</p>';
        } else {
            listEl.innerHTML = biomes
                .map(biome => `<span class="biome-tag">${this.formatBiomeName(biome)}</span>`)
                .join('');
        }
    }
    
    toggleBiomesList() {
        const listEl = document.getElementById('biomes-list');
        const toggleBtn = document.getElementById('biomes-toggle');
        if (!listEl || !toggleBtn) return;
        
        const isHidden = listEl.classList.contains('hidden');
        listEl.classList.toggle('hidden');
        toggleBtn.textContent = isHidden ? '▲' : '▼';
        toggleBtn.title = isHidden ? 'Hide biomes list' : 'Show biomes list';
    }
    
    formatBiomeName(biome) {
        // Convert minecraft:plains -> Plains, minecraft:dark_forest -> Dark Forest
        let name = biome.replace(/^minecraft:/, '');
        return name
            .split('_')
            .map(word => word.charAt(0).toUpperCase() + word.slice(1))
            .join(' ');
    }
    
    async loadLeaderboard() {
        const days = document.getElementById('leaderboard-days')?.value || 7;
        const limit = document.getElementById('leaderboard-limit')?.value || 10;
        
        try {
            const response = await fetch(`/api/public/leaderboard?days=${days}&limit=${limit}`);
            const data = await response.json();
            
            const tbody = document.getElementById('leaderboard-body');
            if (!data.leaderboard || data.leaderboard.length === 0) {
                tbody.innerHTML = '<tr><td colspan="6" class="placeholder">No data available</td></tr>';
                return;
            }
            
            tbody.innerHTML = data.leaderboard.map((row, index) => `
                <tr>
                    <td class="${index < 3 ? 'rank-' + (index + 1) : ''}">${index + 1}</td>
                    <td>${this.escapeHtml(row.name || 'Unknown')}</td>
                    <td>${this.formatDuration(row.playtime_ms || 0)}</td>
                    <td>${this.formatNumber(row.blocks_broken || 0)}</td>
                    <td>${this.formatNumber(row.deaths || 0)}</td>
                    <td>${this.formatNumber(row.mob_kills || 0)}</td>
                </tr>
            `).join('');
        } catch (error) {
            console.error('Failed to load leaderboard:', error);
        }
    }
    
    async loadMoments() {
        const limit = document.getElementById('moments-limit')?.value || 20;
        
        try {
            const response = await fetch(`/api/public/moments?limit=${limit}`);
            const data = await response.json();
            
            const container = document.getElementById('moments-list');
            if (!data.moments || data.moments.length === 0) {
                container.innerHTML = '<p class="placeholder">No moments recorded yet</p>';
                return;
            }
            
            container.innerHTML = data.moments.map(moment => `
                <div class="moment-card">
                    <div class="moment-title">${this.escapeHtml(moment.title)}</div>
                    <div class="moment-detail">${this.escapeHtml(moment.detail)}</div>
                    <div class="moment-meta">
                        <span>${moment.world} (${moment.x}, ${moment.y}, ${moment.z})</span>
                        <span>${this.formatTimestamp(moment.endedAt)}</span>
                    </div>
                </div>
            `).join('');
        } catch (error) {
            console.error('Failed to load moments:', error);
        }
    }
    
    // ============== Admin Data Loading ==============
    
    loadAdminData() {
        if (!this.isAdmin) return;
        this.loadAdminTab('health');
    }
    
    loadAdminTab(tab) {
        if (!this.isAdmin) return;
        
        switch (tab) {
            case 'health':
                this.loadHealth();
                break;
            case 'social':
                this.loadSocial();
                break;
            case 'deaths':
                this.loadDeaths();
                break;
            case 'players':
                this.loadAllPlayers();
                break;
        }
    }
    
    async loadHealth() {
        try {
            const response = await fetch('/api/admin/health');
            if (!response.ok) {
                if (response.status === 404) {
                    document.getElementById('health-chunks').textContent = '-';
                    document.getElementById('health-entities').textContent = '-';
                    document.getElementById('health-hoppers').textContent = '-';
                    document.getElementById('health-redstone').textContent = '-';
                    document.getElementById('cost-index-value').textContent = 'No data yet';
                    return;
                }
                throw new Error('Failed to load health');
            }
            
            const data = await response.json();
            
            document.getElementById('health-chunks').textContent = this.formatNumber(data.chunks);
            document.getElementById('health-entities').textContent = this.formatNumber(data.entities);
            document.getElementById('health-hoppers').textContent = this.formatNumber(data.hoppers);
            document.getElementById('health-redstone').textContent = this.formatNumber(data.redstone);
            
            const costIndex = data.costIndex || 0;
            document.getElementById('cost-index-fill').style.width = `${Math.min(100, costIndex)}%`;
            document.getElementById('cost-index-value').textContent = `${costIndex.toFixed(1)} / 100`;
        } catch (error) {
            console.error('Failed to load health:', error);
        }
    }
    
    async loadHeatmap() {
        const type = document.getElementById('heatmap-type')?.value || 'MINING';
        const world = document.getElementById('heatmap-world')?.value || 'world';
        
        const container = document.getElementById('heatmap-container');
        container.innerHTML = '<p class="placeholder">Loading heatmap...</p>';
        
        try {
            const response = await fetch(`/api/admin/heatmap?type=${type}&world=${world}`);
            if (!response.ok) throw new Error('Failed to load heatmap');
            
            const data = await response.json();
            
            if (!data.bins || data.bins.length === 0) {
                container.innerHTML = '<p class="placeholder">No data for this heatmap</p>';
                return;
            }
            
            // Create canvas heatmap
            this.renderHeatmapCanvas(container, data.bins, data.gridSize);
            document.getElementById('heatmap-legend')?.classList.remove('hidden');
        } catch (error) {
            console.error('Failed to load heatmap:', error);
            container.innerHTML = '<p class="placeholder">Failed to load heatmap</p>';
        }
    }
    
    renderHeatmapCanvas(container, bins, gridSize) {
        // Find bounds
        let minX = Infinity, maxX = -Infinity;
        let minZ = Infinity, maxZ = -Infinity;
        let maxCount = 0;
        
        for (const bin of bins) {
            minX = Math.min(minX, bin.chunkX);
            maxX = Math.max(maxX, bin.chunkX);
            minZ = Math.min(minZ, bin.chunkZ);
            maxZ = Math.max(maxZ, bin.chunkZ);
            maxCount = Math.max(maxCount, bin.count || bin.weight || 0);
        }
        
        const width = maxX - minX + 1;
        const height = maxZ - minZ + 1;
        const scale = Math.min(4, Math.max(1, Math.floor(400 / Math.max(width, height))));
        
        const canvas = document.createElement('canvas');
        canvas.width = width * scale;
        canvas.height = height * scale;
        canvas.className = 'heatmap-canvas';
        
        const ctx = canvas.getContext('2d');
        ctx.fillStyle = '#1a1a2e';
        ctx.fillRect(0, 0, canvas.width, canvas.height);
        
        for (const bin of bins) {
            const x = (bin.chunkX - minX) * scale;
            const z = (bin.chunkZ - minZ) * scale;
            const intensity = (bin.count || bin.weight || 0) / maxCount;
            
            ctx.fillStyle = this.getHeatmapColor(intensity);
            ctx.fillRect(x, z, scale, scale);
        }
        
        container.innerHTML = '';
        container.appendChild(canvas);
    }
    
    getHeatmapColor(intensity) {
        // Gradient from dark blue to green to yellow to red
        if (intensity < 0.25) {
            return `rgb(${Math.round(15 + intensity * 4 * 45)}, ${Math.round(52 + intensity * 4 * 43)}, ${Math.round(96 - intensity * 4 * 26)})`;
        } else if (intensity < 0.5) {
            const t = (intensity - 0.25) * 4;
            return `rgb(${Math.round(26 + t * 48)}, ${Math.round(95 + t * 78)}, ${Math.round(122 - t * 42)})`;
        } else if (intensity < 0.75) {
            const t = (intensity - 0.5) * 4;
            return `rgb(${Math.round(74 + t * 177)}, ${Math.round(173 + t * 18)}, ${Math.round(80 - t * 44)})`;
        } else {
            const t = (intensity - 0.75) * 4;
            return `rgb(${Math.round(251 - t * 3)}, ${Math.round(191 - t * 78)}, ${Math.round(36 + t * 77)})`;
        }
    }
    
    async loadSocial() {
        try {
            const response = await fetch('/api/admin/social?limit=20');
            if (!response.ok) throw new Error('Failed to load social');
            
            const data = await response.json();
            const tbody = document.getElementById('social-body');
            
            if (!data.pairs || data.pairs.length === 0) {
                tbody.innerHTML = '<tr><td colspan="3" class="placeholder">No social data yet</td></tr>';
                return;
            }
            
            tbody.innerHTML = data.pairs.map(pair => `
                <tr>
                    <td>${this.escapeHtml(pair.playerA.name)} & ${this.escapeHtml(pair.playerB.name)}</td>
                    <td>${this.formatDuration(pair.timeTogetherSeconds * 1000)}</td>
                    <td>${this.formatNumber(pair.sharedKills)}</td>
                </tr>
            `).join('');
        } catch (error) {
            console.error('Failed to load social:', error);
        }
    }
    
    async loadDeaths() {
        try {
            const response = await fetch('/api/admin/deaths?limit=20');
            if (!response.ok) throw new Error('Failed to load deaths');
            
            const data = await response.json();
            const container = document.getElementById('deaths-list');
            
            if (!data.deaths || data.deaths.length === 0) {
                container.innerHTML = '<p class="placeholder">No death replays yet</p>';
                return;
            }
            
            container.innerHTML = data.deaths.map(death => `
                <div class="death-entry">
                    <div class="death-icon">💀</div>
                    <div class="death-info">
                        <h4>${this.escapeHtml(death.name)}</h4>
                        <div class="death-details">
                            <div>${this.escapeHtml(death.cause || 'Unknown cause')}</div>
                            <div>${death.world} (${death.x}, ${death.y}, ${death.z})</div>
                            <div>${this.formatTimestamp(death.timestamp)}</div>
                        </div>
                    </div>
                </div>
            `).join('');
        } catch (error) {
            console.error('Failed to load deaths:', error);
        }
    }
    
    async loadAllPlayers() {
        try {
            const response = await fetch('/api/admin/player/all');
            if (!response.ok) throw new Error('Failed to load players');
            
            const data = await response.json();
            this.allPlayers = data.players || [];
            this.renderPlayers(this.allPlayers);
        } catch (error) {
            console.error('Failed to load players:', error);
        }
    }
    
    renderPlayers(players) {
        const tbody = document.getElementById('players-body');
        
        if (!players || players.length === 0) {
            tbody.innerHTML = '<tr><td colspan="5" class="placeholder">No players found</td></tr>';
            return;
        }
        
        tbody.innerHTML = players.map(player => `
            <tr>
                <td>${this.escapeHtml(player.name)}</td>
                <td>${this.formatDuration(player.playtimeMillis)}</td>
                <td>${this.formatNumber(player.deaths)}</td>
                <td>${this.formatNumber(player.playerKills + player.mobKills)}</td>
                <td>${this.formatTimestamp(player.lastJoin)}</td>
            </tr>
        `).join('');
    }
    
    filterPlayers(query) {
        if (!this.allPlayers) return;
        
        const filtered = query 
            ? this.allPlayers.filter(p => p.name.toLowerCase().includes(query.toLowerCase()))
            : this.allPlayers;
        
        this.renderPlayers(filtered);
    }
    
    // ============== Formatting Helpers ==============
    
    formatNumber(num) {
        if (num === null || num === undefined) return '-';
        if (num >= 1000000) return (num / 1000000).toFixed(1) + 'M';
        if (num >= 1000) return (num / 1000).toFixed(1) + 'K';
        return num.toLocaleString();
    }
    
    formatHours(hours) {
        if (hours === null || hours === undefined) return '-';
        if (hours >= 24) return Math.round(hours / 24) + 'd';
        return Math.round(hours) + 'h';
    }
    
    formatDuration(ms) {
        if (!ms) return '-';
        const hours = Math.floor(ms / 3600000);
        const minutes = Math.floor((ms % 3600000) / 60000);
        
        if (hours >= 24) {
            const days = Math.floor(hours / 24);
            return `${days}d ${hours % 24}h`;
        }
        if (hours > 0) {
            return `${hours}h ${minutes}m`;
        }
        return `${minutes}m`;
    }
    
    formatTimestamp(ts) {
        if (!ts) return '-';
        const date = new Date(ts);
        const now = new Date();
        const diff = now - date;
        
        if (diff < 60000) return 'Just now';
        if (diff < 3600000) return `${Math.floor(diff / 60000)}m ago`;
        if (diff < 86400000) return `${Math.floor(diff / 3600000)}h ago`;
        if (diff < 604800000) return `${Math.floor(diff / 86400000)}d ago`;
        
        return date.toLocaleDateString();
    }
    
    escapeHtml(str) {
        if (!str) return '';
        const div = document.createElement('div');
        div.textContent = str;
        return div.innerHTML;
    }
}

// Initialize dashboard when DOM is ready
document.addEventListener('DOMContentLoaded', () => {
    window.dashboard = new SMPStatsDashboard();
});
