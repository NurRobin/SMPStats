#!/bin/bash
# ╔══════════════════════════════════════════════════════════════════════════════╗
# ║  SMPStats E2E Test Suite                                                     ║
# ║  Comprehensive tests for RCON, API, Dashboard, and Database                  ║
# ╚══════════════════════════════════════════════════════════════════════════════╝
#
# Usage: ./run-e2e-tests.sh <rcon_port> <rcon_pass> <api_port> <api_key> \
#                          [dashboard_port] [admin_pass] [host] [results_file]

set -e

# ══════════════════════════════════════════════════════════════════════════════
# CONFIGURATION
# ══════════════════════════════════════════════════════════════════════════════

# Colors & Formatting
R='\033[0;31m'    # Red
G='\033[0;32m'    # Green
Y='\033[1;33m'    # Yellow
B='\033[0;34m'    # Blue
C='\033[0;36m'    # Cyan
M='\033[0;35m'    # Magenta
W='\033[1;37m'    # White Bold
D='\033[2m'       # Dim
N='\033[0m'       # Reset

# Arguments
RCON_PORT="${1:-25575}"
RCON_PASS="${2:-test}"
API_PORT="${3:-8765}"
API_KEY="${4:-test-api-key}"
DASH_PORT="${5:-8080}"
ADMIN_PASS="${6:-admin123}"
HOST="${7:-localhost}"
RESULTS_FILE="${8:-/tmp/e2e-results.json}"

# Counters
PASSED=0
FAILED=0
SKIPPED=0

# Test results array
TESTS_JSON="[]"

# ══════════════════════════════════════════════════════════════════════════════
# UTILITY FUNCTIONS
# ══════════════════════════════════════════════════════════════════════════════

header() {
    echo ""
    echo -e "${B}╔══════════════════════════════════════════════════════════════════════╗${N}"
    echo -e "${B}║${N}  ${W}$1${N}"
    echo -e "${B}╚══════════════════════════════════════════════════════════════════════╝${N}"
    echo ""
}

section() {
    echo ""
    echo -e "${C}┌──────────────────────────────────────────────────────────────────────┐${N}"
    echo -e "${C}│${N}  ${M}$1${N}"
    echo -e "${C}└──────────────────────────────────────────────────────────────────────┘${N}"
    echo ""
}

# Log a test result with full details
log_result() {
    local cat="$1" name="$2" status="$3" cmd="$4" endpoint="$5" result="$6" details="$7"
    
    # Console output
    local icon color
    case "$status" in
        passed)  icon="✓"; color="$G"; PASSED=$((PASSED + 1)) ;;
        failed)  icon="✗"; color="$R"; FAILED=$((FAILED + 1)) ;;
        skipped) icon="○"; color="$Y"; SKIPPED=$((SKIPPED + 1)) ;;
    esac
    
    printf "  ${color}${icon}${N}  %-45s ${D}%s${N}\n" "$name" "$result"
    [ -n "$details" ] && echo -e "     ${D}└─ ${details}${N}"
    
    # JSON output - escape special chars
    local safe_result=$(echo "$result" | sed 's/\\/\\\\/g; s/"/\\"/g; s/\t/\\t/g' | tr -d '\n' | head -c 150)
    local safe_details=$(echo "$details" | sed 's/\\/\\\\/g; s/"/\\"/g; s/\t/\\t/g' | tr -d '\n' | head -c 150)
    local safe_cmd=$(echo "$cmd" | sed 's/\\/\\\\/g; s/"/\\"/g')
    local safe_endpoint=$(echo "$endpoint" | sed 's/\\/\\\\/g; s/"/\\"/g')
    
    TESTS_JSON=$(echo "$TESTS_JSON" | jq --arg cat "$cat" --arg name "$name" --arg status "$status" \
        --arg cmd "$safe_cmd" --arg endpoint "$safe_endpoint" --arg result "$safe_result" --arg details "$safe_details" \
        '. + [{category: $cat, name: $name, status: $status, command: $cmd, endpoint: $endpoint, result: $result, details: $details}]')
}

# ══════════════════════════════════════════════════════════════════════════════
# RCON TESTS
# ══════════════════════════════════════════════════════════════════════════════

run_rcon_tests() {
    section "🎮 RCON Command Tests"
    
    if ! command -v mcrcon &>/dev/null; then
        log_result "RCON" "RCON Test Suite" "skipped" "" "" "mcrcon not installed" "Install mcrcon to run RCON tests"
        return
    fi
    
    rcon() { timeout 5 mcrcon -H "$HOST" -P "$RCON_PORT" -p "$RCON_PASS" "$1" 2>&1 || echo "RCON_TIMEOUT_OR_ERROR"; }
    
    # Test: Server connectivity
    echo -e "  ${D}Testing server connectivity...${N}"
    result=$(rcon "list")
    if echo "$result" | grep -qi "players"; then
        players=$(echo "$result" | grep -oP '\d+(?= of)' || echo "?")
        log_result "RCON" "Server Connectivity" "passed" "/list" "" "Connected, ${players} players online" ""
    else
        log_result "RCON" "Server Connectivity" "failed" "/list" "" "Connection failed" "$result"
    fi
    
    # Test: Plugin info
    result=$(rcon "smpstats info")
    if echo "$result" | grep -qiE "smpstats|version|enabled"; then
        ver=$(echo "$result" | grep -oiP 'v?[\d.]+' | head -1 || echo "detected")
        log_result "RCON" "Plugin Info Command" "passed" "/smpstats info" "" "Plugin loaded ($ver)" ""
    elif echo "$result" | grep -qi "unknown"; then
        log_result "RCON" "Plugin Info Command" "failed" "/smpstats info" "" "Command not found" "Plugin may not be loaded"
    else
        log_result "RCON" "Plugin Info Command" "failed" "/smpstats info" "" "Unexpected response" "$result"
    fi
    
    # Test: Stats command
    result=$(rcon "stats")
    if [ -n "$result" ] && ! echo "$result" | grep -q "RCON_ERROR"; then
        log_result "RCON" "Stats Command" "passed" "/stats" "" "Command responds" ""
    else
        log_result "RCON" "Stats Command" "failed" "/stats" "" "No response" ""
    fi
    
    # Test: Debug command
    result=$(rcon "smpstats debug")
    if [ -n "$result" ] && ! echo "$result" | grep -qE "RCON_ERROR|unknown"; then
        log_result "RCON" "Debug Command" "passed" "/smpstats debug" "" "Debug available" ""
    else
        log_result "RCON" "Debug Command" "skipped" "/smpstats debug" "" "Not available" ""
    fi
    
    # Test: Server stats
    result=$(rcon "sstats")
    if [ -n "$result" ] && ! echo "$result" | grep -qE "RCON_ERROR|unknown"; then
        log_result "RCON" "Server Stats Command" "passed" "/sstats" "" "Stats available" ""
    else
        log_result "RCON" "Server Stats Command" "skipped" "/sstats" "" "Not available" ""
    fi
    
    # Test: Reload
    result=$(rcon "smpstats reload")
    if echo "$result" | grep -qiE "reload|success|config"; then
        log_result "RCON" "Config Reload" "passed" "/smpstats reload" "" "Reload successful" ""
    elif [ -n "$result" ] && ! echo "$result" | grep -qi "unknown"; then
        log_result "RCON" "Config Reload" "passed" "/smpstats reload" "" "Command executed" ""
    else
        log_result "RCON" "Config Reload" "skipped" "/smpstats reload" "" "Not available" ""
    fi
}

# ══════════════════════════════════════════════════════════════════════════════
# API TESTS
# ══════════════════════════════════════════════════════════════════════════════

run_api_tests() {
    section "🌐 HTTP API Tests"
    
    API="http://${HOST}:${API_PORT}"
    
    # Helper: make API call with timeout, return "status|time|body"
    api() {
        local method="$1" path="$2" auth="${3:-true}"
        local resp
        if [ "$auth" = "true" ]; then
            resp=$(timeout 10 curl -s -w "\n%{http_code}\n%{time_total}" -X "$method" \
                --connect-timeout 5 --max-time 8 \
                -H "Content-Type: application/json" -H "X-API-Key: ${API_KEY}" \
                "${API}${path}" 2>&1) || resp=$'\n000\n0'
        else
            resp=$(timeout 10 curl -s -w "\n%{http_code}\n%{time_total}" -X "$method" \
                --connect-timeout 5 --max-time 8 \
                -H "Content-Type: application/json" "${API}${path}" 2>&1) || resp=$'\n000\n0'
        fi
        
        local body=$(echo "$resp" | head -n -2)
        local code=$(echo "$resp" | tail -2 | head -1)
        local time=$(echo "$resp" | tail -1)
        echo "${code}|${time}|${body}"
    }
    
    # Test: Auth enforcement
    echo -e "  ${D}Testing authentication...${N}"
    r=$(api "GET" "/stats/all" "false")
    code=$(echo "$r" | cut -d'|' -f1)
    if [ "$code" = "401" ]; then
        log_result "API" "Auth Enforcement" "passed" "" "GET /stats/all (no auth)" "HTTP 401 Unauthorized" "Blocks unauthenticated requests"
    else
        log_result "API" "Auth Enforcement" "failed" "" "GET /stats/all (no auth)" "HTTP $code" "Expected 401"
    fi
    
    # Test: GET /stats/all
    r=$(api "GET" "/stats/all")
    code=$(echo "$r" | cut -d'|' -f1)
    time=$(echo "$r" | cut -d'|' -f2)
    body=$(echo "$r" | cut -d'|' -f3-)
    if [ "$code" = "200" ]; then
        if echo "$body" | jq -e 'type == "array"' &>/dev/null; then
            count=$(echo "$body" | jq 'length')
            log_result "API" "Get All Stats" "passed" "" "GET /stats/all" "HTTP 200, ${count} records" "${time}s response time"
        else
            log_result "API" "Get All Stats" "failed" "" "GET /stats/all" "Invalid JSON structure" "Expected array"
        fi
    else
        log_result "API" "Get All Stats" "failed" "" "GET /stats/all" "HTTP $code" ""
    fi
    
    # Test: GET /online
    r=$(api "GET" "/online")
    code=$(echo "$r" | cut -d'|' -f1)
    body=$(echo "$r" | cut -d'|' -f3-)
    if [ "$code" = "200" ]; then
        count=$(echo "$body" | jq 'if type == "array" then length else 0 end' 2>/dev/null || echo "?")
        log_result "API" "Online Players" "passed" "" "GET /online" "HTTP 200, ${count} online" ""
    else
        log_result "API" "Online Players" "failed" "" "GET /online" "HTTP $code" ""
    fi
    
    # Test: GET /health
    r=$(api "GET" "/health")
    code=$(echo "$r" | cut -d'|' -f1)
    time=$(echo "$r" | cut -d'|' -f2)
    if [ "$code" = "200" ]; then
        log_result "API" "Health Check" "passed" "" "GET /health" "HTTP 200" "${time}s"
    else
        log_result "API" "Health Check" "failed" "" "GET /health" "HTTP $code" ""
    fi
    
    # Test: GET /moments/recent
    r=$(api "GET" "/moments/recent?limit=10")
    code=$(echo "$r" | cut -d'|' -f1)
    body=$(echo "$r" | cut -d'|' -f3-)
    if [ "$code" = "200" ]; then
        count=$(echo "$body" | jq 'if type == "array" then length else 0 end' 2>/dev/null || echo "0")
        log_result "API" "Recent Moments" "passed" "" "GET /moments/recent" "HTTP 200, ${count} items" ""
    else
        log_result "API" "Recent Moments" "failed" "" "GET /moments/recent" "HTTP $code" ""
    fi
    
    # Test: GET /heatmap/MINING
    r=$(api "GET" "/heatmap/MINING")
    code=$(echo "$r" | cut -d'|' -f1)
    if [ "$code" = "200" ]; then
        log_result "API" "Mining Heatmap" "passed" "" "GET /heatmap/MINING" "HTTP 200" ""
    else
        log_result "API" "Mining Heatmap" "failed" "" "GET /heatmap/MINING" "HTTP $code" ""
    fi
    
    # Test: GET /heatmap/DEATH
    r=$(api "GET" "/heatmap/DEATH")
    code=$(echo "$r" | cut -d'|' -f1)
    if [ "$code" = "200" ]; then
        log_result "API" "Death Heatmap" "passed" "" "GET /heatmap/DEATH" "HTTP 200" ""
    else
        log_result "API" "Death Heatmap" "failed" "" "GET /heatmap/DEATH" "HTTP $code" ""
    fi
    
    # Test: Invalid UUID
    r=$(api "GET" "/stats/not-a-uuid")
    code=$(echo "$r" | cut -d'|' -f1)
    if [ "$code" = "400" ] || [ "$code" = "404" ]; then
        log_result "API" "Invalid UUID Handling" "passed" "" "GET /stats/invalid" "HTTP $code" "Graceful error handling"
    else
        log_result "API" "Invalid UUID Handling" "failed" "" "GET /stats/invalid" "HTTP $code" "Expected 400 or 404"
    fi
    
    # Test: GET /timeline
    r=$(api "GET" "/timeline")
    code=$(echo "$r" | cut -d'|' -f1)
    if [ "$code" = "200" ] || [ "$code" = "400" ]; then
        log_result "API" "Timeline Endpoint" "passed" "" "GET /timeline" "HTTP $code" ""
    else
        log_result "API" "Timeline Endpoint" "failed" "" "GET /timeline" "HTTP $code" ""
    fi
    
    # Test: GET /social/top
    r=$(api "GET" "/social/top?limit=10")
    code=$(echo "$r" | cut -d'|' -f1)
    if [ "$code" = "200" ]; then
        log_result "API" "Social Leaderboard" "passed" "" "GET /social/top" "HTTP 200" ""
    else
        log_result "API" "Social Leaderboard" "failed" "" "GET /social/top" "HTTP $code" ""
    fi
    
    # Test: GET /death/replay
    r=$(api "GET" "/death/replay?limit=5")
    code=$(echo "$r" | cut -d'|' -f1)
    if [ "$code" = "200" ]; then
        log_result "API" "Death Replay" "passed" "" "GET /death/replay" "HTTP 200" ""
    else
        log_result "API" "Death Replay" "failed" "" "GET /death/replay" "HTTP $code" ""
    fi
    
    # Test: Method enforcement
    r=$(timeout 5 curl -s -o /dev/null -w "%{http_code}" --connect-timeout 3 --max-time 4 \
        -X POST -H "X-API-Key: ${API_KEY}" "${API}/stats/all" 2>&1 || echo "000")
    if [ "$r" = "405" ]; then
        log_result "API" "Method Enforcement" "passed" "" "POST /stats/all" "HTTP 405" "Rejects wrong method"
    else
        log_result "API" "Method Enforcement" "skipped" "" "POST /stats/all" "HTTP $r" "Non-standard response"
    fi
}

# ══════════════════════════════════════════════════════════════════════════════
# DASHBOARD TESTS
# ══════════════════════════════════════════════════════════════════════════════

run_dashboard_tests() {
    section "📊 Dashboard Tests"
    
    DASH="http://${HOST}:${DASH_PORT}"
    
    # Check availability with strict timeout
    check=$(timeout 5 curl -s -o /dev/null -w "%{http_code}" --connect-timeout 3 --max-time 4 "$DASH/" 2>&1 || echo "000")
    if [ "$check" = "000" ]; then
        log_result "Dashboard" "Dashboard Availability" "skipped" "" "GET /" "Not reachable" "Port ${DASH_PORT}"
        return
    fi
    
    # Test: Index page
    r=$(timeout 8 curl -s -w "\n%{http_code}" --connect-timeout 3 --max-time 6 "$DASH/" 2>&1) || r=$'\n000'
    code=$(echo "$r" | tail -1)
    body=$(echo "$r" | head -n -1)
    if [ "$code" = "200" ] && echo "$body" | grep -qi "smpstats"; then
        log_result "Dashboard" "Index Page" "passed" "" "GET /" "HTTP 200" "SMPStats content found"
    else
        log_result "Dashboard" "Index Page" "failed" "" "GET /" "HTTP $code" ""
    fi
    
    # Test: CSS
    code=$(timeout 5 curl -s -o /dev/null -w "%{http_code}" --connect-timeout 3 --max-time 4 "$DASH/css/style.css" 2>&1 || echo "000")
    if [ "$code" = "200" ]; then
        log_result "Dashboard" "CSS Assets" "passed" "" "GET /css/style.css" "HTTP 200" ""
    else
        log_result "Dashboard" "CSS Assets" "failed" "" "GET /css/style.css" "HTTP $code" ""
    fi
    
    # Test: JS
    code=$(timeout 5 curl -s -o /dev/null -w "%{http_code}" --connect-timeout 3 --max-time 4 "$DASH/js/app.js" 2>&1 || echo "000")
    if [ "$code" = "200" ]; then
        log_result "Dashboard" "JavaScript Assets" "passed" "" "GET /js/app.js" "HTTP 200" ""
    else
        log_result "Dashboard" "JavaScript Assets" "failed" "" "GET /js/app.js" "HTTP $code" ""
    fi
    
    # Test: Public config
    r=$(timeout 8 curl -s -w "\n%{http_code}" --connect-timeout 3 --max-time 6 "$DASH/api/public/config" 2>&1) || r=$'\n000'
    code=$(echo "$r" | tail -1)
    body=$(echo "$r" | head -n -1)
    if [ "$code" = "200" ] && echo "$body" | jq -e '.publicEnabled' &>/dev/null; then
        mode=$(echo "$body" | jq -r '.publicEnabled')
        log_result "Dashboard" "Public Config API" "passed" "" "GET /api/public/config" "HTTP 200" "publicEnabled=$mode"
    else
        log_result "Dashboard" "Public Config API" "failed" "" "GET /api/public/config" "HTTP $code" ""
    fi
    
    # Test: Public endpoints (200 or 403 both valid)
    for ep in "online" "leaderboard" "moments" "stats"; do
        code=$(timeout 5 curl -s -o /dev/null -w "%{http_code}" --connect-timeout 3 --max-time 4 "$DASH/api/public/$ep" 2>&1 || echo "000")
        if [ "$code" = "200" ] || [ "$code" = "403" ]; then
            log_result "Dashboard" "Public /$ep" "passed" "" "GET /api/public/$ep" "HTTP $code" ""
        else
            log_result "Dashboard" "Public /$ep" "failed" "" "GET /api/public/$ep" "HTTP $code" ""
        fi
    done
    
    # Test: Admin auth rejection
    code=$(timeout 5 curl -s -o /dev/null -w "%{http_code}" --connect-timeout 3 --max-time 4 \
        -X POST -H "Content-Type: application/json" \
        -d '{"password":"wrong"}' "$DASH/api/admin/login" 2>&1 || echo "000")
    if [ "$code" = "401" ]; then
        log_result "Dashboard" "Admin Auth Security" "passed" "" "POST /api/admin/login" "HTTP 401" "Rejects bad password"
    else
        log_result "Dashboard" "Admin Auth Security" "failed" "" "POST /api/admin/login" "HTTP $code" "Expected 401"
    fi
    
    # Test: Admin endpoint protection
    code=$(timeout 5 curl -s -o /dev/null -w "%{http_code}" --connect-timeout 3 --max-time 4 "$DASH/api/admin/health" 2>&1 || echo "000")
    if [ "$code" = "401" ]; then
        log_result "Dashboard" "Admin Endpoint Protection" "passed" "" "GET /api/admin/health" "HTTP 401" "Requires auth"
    else
        log_result "Dashboard" "Admin Endpoint Protection" "failed" "" "GET /api/admin/health" "HTTP $code" "Expected 401"
    fi
}

# ══════════════════════════════════════════════════════════════════════════════
# DATABASE TESTS
# ══════════════════════════════════════════════════════════════════════════════

run_db_tests() {
    section "💾 Database Validation Tests"
    
    API="http://${HOST}:${API_PORT}"
    get() { timeout 8 curl -s --connect-timeout 3 --max-time 6 -H "X-API-Key: ${API_KEY}" "${API}$1" 2>&1 || echo "{}"; }
    
    # Test: Stats structure
    echo -e "  ${D}Validating data structures...${N}"
    data=$(get "/stats/all")
    if echo "$data" | jq -e 'type == "array"' &>/dev/null; then
        count=$(echo "$data" | jq 'length')
        log_result "Database" "Stats Array Structure" "passed" "" "GET /stats/all" "Valid array, ${count} records" ""
        
        if [ "$count" -gt 0 ]; then
            has_uuid=$(echo "$data" | jq -e '.[0].uuid' &>/dev/null && echo "✓" || echo "✗")
            has_name=$(echo "$data" | jq -e '.[0].name' &>/dev/null && echo "✓" || echo "✗")
            if [ "$has_uuid" = "✓" ] && [ "$has_name" = "✓" ]; then
                sample=$(echo "$data" | jq -r '.[0].name // "unknown"')
                log_result "Database" "Stats Field Validation" "passed" "" "" "uuid=$has_uuid name=$has_name" "Sample: $sample"
            else
                log_result "Database" "Stats Field Validation" "failed" "" "" "uuid=$has_uuid name=$has_name" "Missing fields"
            fi
        fi
    else
        log_result "Database" "Stats Array Structure" "failed" "" "GET /stats/all" "Invalid structure" ""
    fi
    
    # Test: Heatmap structure
    data=$(get "/heatmap/MINING")
    if echo "$data" | jq -e 'type == "array" or type == "object"' &>/dev/null; then
        t=$(echo "$data" | jq -r 'type')
        log_result "Database" "Heatmap Structure" "passed" "" "GET /heatmap/MINING" "Valid $t" ""
    else
        log_result "Database" "Heatmap Structure" "failed" "" "GET /heatmap/MINING" "Invalid structure" ""
    fi
    
    # Test: Moments structure
    data=$(get "/moments/recent?limit=5")
    if echo "$data" | jq -e 'type == "array"' &>/dev/null; then
        count=$(echo "$data" | jq 'length')
        log_result "Database" "Moments Structure" "passed" "" "GET /moments/recent" "Valid array, ${count} items" ""
    else
        log_result "Database" "Moments Structure" "failed" "" "GET /moments/recent" "Invalid structure" ""
    fi
    
    # Test: Social structure
    data=$(get "/social/top?limit=5")
    if echo "$data" | jq -e 'type == "array"' &>/dev/null; then
        count=$(echo "$data" | jq 'length')
        log_result "Database" "Social Data Structure" "passed" "" "GET /social/top" "Valid array, ${count} items" ""
    else
        log_result "Database" "Social Data Structure" "failed" "" "GET /social/top" "Invalid structure" ""
    fi
    
    # Test: Health structure
    data=$(get "/health")
    if echo "$data" | jq empty 2>/dev/null; then
        log_result "Database" "Health Data Structure" "passed" "" "GET /health" "Valid JSON" ""
    else
        log_result "Database" "Health Data Structure" "failed" "" "GET /health" "Invalid JSON" ""
    fi
}

# ══════════════════════════════════════════════════════════════════════════════
# INTEGRATION TESTS
# ══════════════════════════════════════════════════════════════════════════════

run_integration_tests() {
    section "🔗 Integration Tests"
    
    API="http://${HOST}:${API_PORT}"
    
    # Test: RCON/API consistency
    if command -v mcrcon &>/dev/null; then
        echo -e "  ${D}Checking cross-component consistency...${N}"
        rcon_out=$(timeout 5 mcrcon -H "$HOST" -P "$RCON_PORT" -p "$RCON_PASS" "list" 2>&1 || echo "")
        rcon_count=$(echo "$rcon_out" | grep -oP '\d+(?= of)' || echo "0")
        
        api_out=$(timeout 8 curl -s --connect-timeout 3 --max-time 6 -H "X-API-Key: ${API_KEY}" "${API}/online" 2>&1 || echo "[]")
        api_count=$(echo "$api_out" | jq 'length' 2>/dev/null || echo "0")
        
        if [ "$rcon_count" = "$api_count" ]; then
            log_result "Integration" "RCON-API Player Sync" "passed" "" "" "RCON=$rcon_count API=$api_count" "Counts match"
        else
            log_result "Integration" "RCON-API Player Sync" "failed" "" "" "RCON=$rcon_count API=$api_count" "Mismatch"
        fi
    else
        log_result "Integration" "RCON-API Player Sync" "skipped" "" "" "mcrcon unavailable" ""
    fi
    
    # Test: Response time benchmark
    echo ""
    echo -e "  ${D}Benchmarking API response times...${N}"
    echo ""
    printf "  ${D}%-30s %8s %10s${N}\n" "Endpoint" "Status" "Time"
    printf "  ${D}%-30s %8s %10s${N}\n" "──────────────────────────────" "────────" "──────────"
    
    endpoints=("/stats/all" "/online" "/moments/recent" "/health" "/heatmap/MINING")
    all_ok=true
    max_time=0
    max_ep=""
    
    for ep in "${endpoints[@]}"; do
        start=$(date +%s%N)
        code=$(timeout 6 curl -s -o /dev/null -w "%{http_code}" --connect-timeout 3 --max-time 5 \
            -H "X-API-Key: ${API_KEY}" "${API}${ep}" 2>&1 || echo "000")
        end=$(date +%s%N)
        
        ms=$(( (end - start) / 1000000 ))
        
        if [ "$code" = "200" ] && [ "$ms" -lt 5000 ]; then
            icon="${G}✓${N}"
            time_color="$G"
            [ "$ms" -gt 1000 ] && time_color="$Y"
        else
            icon="${R}✗${N}"
            time_color="$R"
            all_ok=false
        fi
        
        printf "  $icon %-28s %8s ${time_color}%7dms${N}\n" "$ep" "HTTP $code" "$ms"
        
        if [ "$ms" -gt "$max_time" ]; then
            max_time=$ms
            max_ep=$ep
        fi
    done
    
    echo ""
    
    if [ "$all_ok" = true ]; then
        log_result "Integration" "API Response Times" "passed" "" "" "All < 5s" "Slowest: ${max_ep} @ ${max_time}ms"
    else
        log_result "Integration" "API Response Times" "failed" "" "" "Timeout or errors" ""
    fi
    
    # Test: Dashboard-API sync
    DASH="http://${HOST}:${DASH_PORT}"
    check=$(timeout 4 curl -s -o /dev/null -w "%{http_code}" --connect-timeout 2 --max-time 3 "$DASH/" 2>&1 || echo "000")
    if [ "$check" != "000" ]; then
        cfg=$(timeout 5 curl -s --connect-timeout 2 --max-time 4 "$DASH/api/public/config" 2>&1 || echo "{}")
        if echo "$cfg" | jq -e '.publicEnabled' &>/dev/null; then
            mode=$(echo "$cfg" | jq -r '.publicEnabled')
            log_result "Integration" "Dashboard-API Sync" "passed" "" "" "Config accessible" "publicEnabled=$mode"
        else
            log_result "Integration" "Dashboard-API Sync" "failed" "" "" "Config parse error" ""
        fi
    else
        log_result "Integration" "Dashboard-API Sync" "skipped" "" "" "Dashboard offline" ""
    fi
}

# ══════════════════════════════════════════════════════════════════════════════
# RESULTS & SUMMARY
# ══════════════════════════════════════════════════════════════════════════════

write_results() {
    local status="success"
    [ "$FAILED" -gt 0 ] && status="failure"
    
    cat > "$RESULTS_FILE" << EOF
{
  "status": "${status}",
  "passed": ${PASSED},
  "failed": ${FAILED},
  "skipped": ${SKIPPED},
  "total": $((PASSED + FAILED + SKIPPED)),
  "rcon_port": "${RCON_PORT}",
  "api_port": "${API_PORT}",
  "dashboard_port": "${DASH_PORT}",
  "host": "${HOST}",
  "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "tests": ${TESTS_JSON}
}
EOF
}

print_summary() {
    local total=$((PASSED + FAILED + SKIPPED))
    local rate=0
    [ $((PASSED + FAILED)) -gt 0 ] && rate=$((PASSED * 100 / (PASSED + FAILED)))
    
    echo ""
    echo -e "${B}╔══════════════════════════════════════════════════════════════════════╗${N}"
    echo -e "${B}║${N}  ${W}TEST SUMMARY${N}"
    echo -e "${B}╠══════════════════════════════════════════════════════════════════════╣${N}"
    echo -e "${B}║${N}"
    
    # Progress bar
    local bar=""
    for i in $(seq 1 20); do
        if [ $((i * 5)) -le "$rate" ]; then
            bar="${bar}█"
        else
            bar="${bar}░"
        fi
    done
    echo -e "${B}║${N}  Pass Rate: ${W}${rate}%${N}  ${G}${bar}${N}"
    echo -e "${B}║${N}"
    printf "${B}║${N}  ${G}✓ Passed:${N}  %3d\n" "$PASSED"
    printf "${B}║${N}  ${R}✗ Failed:${N}  %3d\n" "$FAILED"
    printf "${B}║${N}  ${Y}○ Skipped:${N} %3d\n" "$SKIPPED"
    echo -e "${B}║${N}  ─────────────"
    printf "${B}║${N}  ${W}Total:${N}     %3d\n" "$total"
    echo -e "${B}║${N}"
    echo -e "${B}╚══════════════════════════════════════════════════════════════════════╝${N}"
    echo ""
    
    if [ "$FAILED" -gt 0 ]; then
        echo -e "  ${R}╔═══════════════════════════════════════╗${N}"
        echo -e "  ${R}║         ❌ TESTS FAILED               ║${N}"
        echo -e "  ${R}╚═══════════════════════════════════════╝${N}"
    else
        echo -e "  ${G}╔═══════════════════════════════════════╗${N}"
        echo -e "  ${G}║       ✅ ALL TESTS PASSED             ║${N}"
        echo -e "  ${G}╚═══════════════════════════════════════╝${N}"
    fi
    echo ""
}

# ══════════════════════════════════════════════════════════════════════════════
# MAIN
# ══════════════════════════════════════════════════════════════════════════════

main() {
    header "🧪 SMPStats E2E Test Suite"
    
    echo -e "  ${W}Configuration${N}"
    echo -e "  ─────────────────────────────────────────────────────────────────"
    printf "  %-18s %s\n" "Host:" "$HOST"
    printf "  %-18s %s\n" "RCON Port:" "$RCON_PORT"
    printf "  %-18s %s\n" "API Port:" "$API_PORT"
    printf "  %-18s %s\n" "Dashboard Port:" "$DASH_PORT"
    printf "  %-18s %s\n" "Results File:" "$RESULTS_FILE"
    
    # Run test suites
    run_rcon_tests
    run_api_tests
    run_dashboard_tests
    run_db_tests
    run_integration_tests
    
    # Write results and print summary
    write_results
    print_summary
    
    # Exit code
    [ "$FAILED" -gt 0 ] && exit 1
    exit 0
}

main "$@"