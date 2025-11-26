package de.nurrobin.smpstats.listeners;

import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.StatsService;
import de.nurrobin.smpstats.social.SocialStatsService;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.projectiles.ProjectileSource;

public class CombatListener implements Listener {
    private final SMPStats plugin;
    private final StatsService statsService;
    private final SocialStatsService socialStatsService;

    public CombatListener(SMPStats plugin, StatsService statsService, SocialStatsService socialStatsService) {
        this.plugin = plugin;
        this.statsService = statsService;
        this.socialStatsService = socialStatsService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        String cause = event.getEntity().getLastDamageCause() != null
                ? event.getEntity().getLastDamageCause().getCause().name()
                : "UNKNOWN";
        statsService.addDeath(victim.getUniqueId(), cause);

        Player killer = victim.getKiller();
        if (killer != null) {
            statsService.addPlayerKill(killer.getUniqueId());
            if (socialStatsService != null) {
                socialStatsService.recordSharedKill(killer, true);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player) {
            return;
        }
        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            statsService.addMobKill(killer.getUniqueId());
            if (socialStatsService != null) {
                socialStatsService.recordSharedKill(killer, false);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        Player attacker = resolvePlayer(event.getDamager());
        if (attacker != null) {
            statsService.addDamageDealt(attacker.getUniqueId(), event.getFinalDamage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageTaken(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.SUICIDE || "KILL".equalsIgnoreCase(cause.name())) {
            return; // ignore /kill or self-kill to avoid massive damage spikes
        }
        statsService.addDamageTaken(player.getUniqueId(), event.getFinalDamage());
    }

    private Player resolvePlayer(Object damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            if (source instanceof Player shooter) {
                return shooter;
            }
        }
        return null;
    }
}
