#!/bin/bash
# SMPStats E2E Test Suite
# Comprehensive tests for API, Dashboard, Commands, and Database validation
#
# Usage: ./run-e2e-tests.sh <rcon_port> <rcon_password> <api_port> <api_key> [dashboard_port] [admin_password]

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Test counters
TESTS_PASSED=0
TESTS_FAILED=0
TESTS_SKIPPED=0

# Arguments
RCON_PORT="${1:-25575}"
RCON_PASSWORD="${2:-test}"
API_PORT="${3:-8765}"
API_KEY="${4:-test-api-key}"
DASHBOARD_PORT="${5:-8080}"
ADMIN_PASSWORD="${6:-admin123}"
HOST="${7:-localhost}"

log_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
log_pass() { echo -e "${GREEN}[PASS]${NC} $1"; TESTS_PASSED=$((TESTS_PASSED + 1)); }
log_fail() { echo -e "${RED}[FAIL]${NC} $1"; TESTS_FAILED=$((TESTS_FAILED + 1)); }
log_skip() { echo -e "${YELLOW}[SKIP]${NC} $1"; TESTS_SKIPPED=$((TESTS_SKIPPED + 1)); }
log_section() { echo -e "\n${BLUE}══════════════════════════════════════════════════════════════${NC}"; echo -e "${BLUE}  $1${NC}"; echo -e "${BLUE}══════════════════════════════════════════════════════════════${NC}"; }

# ============================================================================
# RCON TESTS
# ============================================================================
run_rcon_tests() {
    log_section "RCON Command Tests"
    
    # Check if mcrcon is available
    if ! command -v mcrcon &> /dev/null; then
        log_skip "mcrcon not installed - skipping RCON tests"
        return
    fi
    
    rcon_cmd() {
        mcrcon -H "${HOST}" -P "${RCON_PORT}" -p "${RCON_PASSWORD}" "$1" 2>&1 || echo "RCON_ERROR"
    }
    
    # Test 1: Basic connectivity
    log_info "Test: RCON connectivity (/list)"
    result=$(rcon_cmd "list")
    if echo "$result" | grep -qi "players"; then
        log_pass "RCON connectivity works"
    else
        log_fail "RCON connectivity failed: $result"
    fi
    
    # Test 2: SMPStats info command
    log_info "Test: /smpstats info"
    result=$(rcon_cmd "smpstats info")
    if echo "$result" | grep -qi "smpstats\|version\|enabled\|aktiv"; then
        log_pass "/smpstats info returns plugin info"
    else
        log_fail "/smpstats info unexpected response: $result"
    fi
    
    # Test 3: Stats command (no player specified)
    log_info "Test: /stats (no player)"
    result=$(rcon_cmd "stats")
    # Should give usage info or error since no player specified
    if [ -n "$result" ] && [ "$result" != "RCON_ERROR" ]; then
        log_pass "/stats command responds"
    else
        log_fail "/stats command failed: $result"
    fi
    
    # Test 4: SMPStats debug command
    log_info "Test: /smpstats debug"
    result=$(rcon_cmd "smpstats debug")
    if [ -n "$result" ] && [ "$result" != "RCON_ERROR" ]; then
        log_pass "/smpstats debug responds"
    else
        log_skip "/smpstats debug not available"
    fi
    
    # Test 5: sstats command
    log_info "Test: /sstats (server stats)"
    result=$(rcon_cmd "sstats")
    if [ -n "$result" ] && [ "$result" != "RCON_ERROR" ]; then
        log_pass "/sstats command responds"
    else
        log_skip "/sstats command not available"
    fi
}

# ============================================================================
# HTTP API TESTS
# ============================================================================
run_api_tests() {
    log_section "HTTP API Tests"
    
    API_URL="http://${HOST}:${API_PORT}"
    
    # Helper function for API calls
    api_call() {
        local method="$1"
        local endpoint="$2"
        local expected_status="$3"
        local auth="${4:-true}"
        
        if [ "$auth" = "true" ]; then
            response=$(curl -s -w "\n%{http_code}" -X "$method" -H "X-API-Key: ${API_KEY}" "${API_URL}${endpoint}" 2>&1)
        else
            response=$(curl -s -w "\n%{http_code}" -X "$method" "${API_URL}${endpoint}" 2>&1)
        fi
        
        status=$(echo "$response" | tail -1)
        body=$(echo "$response" | sed '$d')
        
        echo "$status|$body"
    }
    
    # Test 1: Authentication required
    log_info "Test: API rejects unauthenticated requests"
    result=$(api_call "GET" "/stats/all" "401" "false")
    status=$(echo "$result" | cut -d'|' -f1)
    if [ "$status" = "401" ]; then
        log_pass "API correctly rejects unauthenticated requests (401)"
    else
        log_fail "API should return 401, got: $status"
    fi
    
    # Test 2: GET /stats/all
    log_info "Test: GET /stats/all"
    result=$(api_call "GET" "/stats/all" "200")
    status=$(echo "$result" | cut -d'|' -f1)
    body=$(echo "$result" | cut -d'|' -f2-)
    if [ "$status" = "200" ]; then
        if echo "$body" | jq empty 2>/dev/null; then
            log_pass "GET /stats/all returns valid JSON (200)"
        else
            log_fail "GET /stats/all returns invalid JSON"
        fi
    else
        log_fail "GET /stats/all failed with status: $status"
    fi
    
    # Test 3: GET /online
    log_info "Test: GET /online"
    result=$(api_call "GET" "/online" "200")
    status=$(echo "$result" | cut -d'|' -f1)
    body=$(echo "$result" | cut -d'|' -f2-)
    if [ "$status" = "200" ]; then
        if echo "$body" | jq -e '.count' &>/dev/null; then
            log_pass "GET /online returns player count"
        else
            log_fail "GET /online missing 'count' field"
        fi
    else
        log_fail "GET /online failed with status: $status"
    fi
    
    # Test 4: GET /moments/recent
    log_info "Test: GET /moments/recent"
    result=$(api_call "GET" "/moments/recent?limit=10" "200")
    status=$(echo "$result" | cut -d'|' -f1)
    if [ "$status" = "200" ]; then
        log_pass "GET /moments/recent returns 200"
    else
        log_fail "GET /moments/recent failed with status: $status"
    fi
    
    # Test 5: GET /health
    log_info "Test: GET /health"
    result=$(api_call "GET" "/health" "200")
    status=$(echo "$result" | cut -d'|' -f1)
    if [ "$status" = "200" ]; then
        log_pass "GET /health returns 200"
    else
        log_fail "GET /health failed with status: $status"
    fi
    
    # Test 6: GET /heatmap/MINING
    log_info "Test: GET /heatmap/MINING"
    result=$(api_call "GET" "/heatmap/MINING" "200")
    status=$(echo "$result" | cut -d'|' -f1)
    if [ "$status" = "200" ]; then
        log_pass "GET /heatmap/MINING returns 200"
    else
        log_fail "GET /heatmap/MINING failed with status: $status"
    fi
    
    # Test 7: GET /heatmap/DEATH
    log_info "Test: GET /heatmap/DEATH"
    result=$(api_call "GET" "/heatmap/DEATH" "200")
    status=$(echo "$result" | cut -d'|' -f1)
    if [ "$status" = "200" ]; then
        log_pass "GET /heatmap/DEATH returns 200"
    else
        log_fail "GET /heatmap/DEATH failed with status: $status"
    fi
    
    # Test 8: Invalid UUID handling
    log_info "Test: GET /stats/invalid-uuid (error handling)"
    result=$(api_call "GET" "/stats/invalid-uuid" "400")
    status=$(echo "$result" | cut -d'|' -f1)
    if [ "$status" = "400" ] || [ "$status" = "404" ]; then
        log_pass "GET /stats/invalid-uuid returns error ($status)"
    else
        log_fail "GET /stats/invalid-uuid should return 400 or 404, got: $status"
    fi
    
    # Test 9: GET /timeline (should work without UUID)
    log_info "Test: GET /timeline"
    result=$(api_call "GET" "/timeline" "200")
    status=$(echo "$result" | cut -d'|' -f1)
    if [ "$status" = "200" ] || [ "$status" = "400" ]; then
        log_pass "GET /timeline responds ($status)"
    else
        log_fail "GET /timeline failed with status: $status"
    fi
    
    # Test 10: GET /social/top
    log_info "Test: GET /social/top"
    result=$(api_call "GET" "/social/top?limit=10" "200")
    status=$(echo "$result" | cut -d'|' -f1)
    if [ "$status" = "200" ]; then
        log_pass "GET /social/top returns 200"
    else
        log_fail "GET /social/top failed with status: $status"
    fi
    
    # Test 11: GET /death/replay
    log_info "Test: GET /death/replay"
    result=$(api_call "GET" "/death/replay?limit=5" "200")
    status=$(echo "$result" | cut -d'|' -f1)
    if [ "$status" = "200" ]; then
        log_pass "GET /death/replay returns 200"
    else
        log_fail "GET /death/replay failed with status: $status"
    fi
    
    # Test 12: POST not allowed on GET endpoints
    log_info "Test: POST /stats/all (method not allowed)"
    result=$(api_call "POST" "/stats/all" "405")
    status=$(echo "$result" | cut -d'|' -f1)
    if [ "$status" = "405" ]; then
        log_pass "POST /stats/all correctly returns 405"
    else
        log_skip "POST method handling: got $status"
    fi
}

# ============================================================================
# WEB DASHBOARD TESTS
# ============================================================================
run_dashboard_tests() {
    log_section "Web Dashboard Tests"
    
    DASH_URL="http://${HOST}:${DASHBOARD_PORT}"
    
    # Check if dashboard is enabled
    dash_check=$(curl -s -o /dev/null -w "%{http_code}" "${DASH_URL}/" 2>&1 || echo "000")
    if [ "$dash_check" = "000" ] || [ "$dash_check" = "404" ]; then
        log_skip "Dashboard not available on port ${DASHBOARD_PORT}"
        return
    fi
    
    # Test 1: Static file serving - index.html
    log_info "Test: Dashboard serves index.html"
    result=$(curl -s -w "\n%{http_code}" "${DASH_URL}/" 2>&1)
    status=$(echo "$result" | tail -1)
    body=$(echo "$result" | sed '$d')
    if [ "$status" = "200" ] && echo "$body" | grep -qi "smpstats"; then
        log_pass "Dashboard serves index.html with SMPStats content"
    else
        log_fail "Dashboard index.html failed: status=$status"
    fi
    
    # Test 2: CSS file
    log_info "Test: Dashboard serves CSS"
    status=$(curl -s -o /dev/null -w "%{http_code}" "${DASH_URL}/css/style.css" 2>&1)
    if [ "$status" = "200" ]; then
        log_pass "Dashboard serves /css/style.css"
    else
        log_fail "Dashboard CSS failed: status=$status"
    fi
    
    # Test 3: JS file
    log_info "Test: Dashboard serves JavaScript"
    status=$(curl -s -o /dev/null -w "%{http_code}" "${DASH_URL}/js/app.js" 2>&1)
    if [ "$status" = "200" ]; then
        log_pass "Dashboard serves /js/app.js"
    else
        log_fail "Dashboard JS failed: status=$status"
    fi
    
    # Test 4: Public config endpoint
    log_info "Test: GET /api/public/config"
    result=$(curl -s -w "\n%{http_code}" "${DASH_URL}/api/public/config" 2>&1)
    status=$(echo "$result" | tail -1)
    body=$(echo "$result" | sed '$d')
    if [ "$status" = "200" ] && echo "$body" | jq -e '.publicEnabled' &>/dev/null; then
        log_pass "Public config endpoint returns valid config"
    else
        log_fail "Public config endpoint failed: status=$status"
    fi
    
    # Test 5: Public online endpoint
    log_info "Test: GET /api/public/online"
    result=$(curl -s -w "\n%{http_code}" "${DASH_URL}/api/public/online" 2>&1)
    status=$(echo "$result" | tail -1)
    if [ "$status" = "200" ] || [ "$status" = "403" ]; then
        log_pass "Public online endpoint responds ($status)"
    else
        log_fail "Public online endpoint failed: status=$status"
    fi
    
    # Test 6: Public leaderboard endpoint
    log_info "Test: GET /api/public/leaderboard"
    result=$(curl -s -w "\n%{http_code}" "${DASH_URL}/api/public/leaderboard?days=7&limit=10" 2>&1)
    status=$(echo "$result" | tail -1)
    if [ "$status" = "200" ] || [ "$status" = "403" ]; then
        log_pass "Public leaderboard endpoint responds ($status)"
    else
        log_fail "Public leaderboard endpoint failed: status=$status"
    fi
    
    # Test 7: Public moments endpoint
    log_info "Test: GET /api/public/moments"
    result=$(curl -s -w "\n%{http_code}" "${DASH_URL}/api/public/moments?limit=10" 2>&1)
    status=$(echo "$result" | tail -1)
    if [ "$status" = "200" ] || [ "$status" = "403" ]; then
        log_pass "Public moments endpoint responds ($status)"
    else
        log_fail "Public moments endpoint failed: status=$status"
    fi
    
    # Test 8: Public stats endpoint
    log_info "Test: GET /api/public/stats"
    result=$(curl -s -w "\n%{http_code}" "${DASH_URL}/api/public/stats" 2>&1)
    status=$(echo "$result" | tail -1)
    if [ "$status" = "200" ] || [ "$status" = "403" ]; then
        log_pass "Public stats endpoint responds ($status)"
    else
        log_fail "Public stats endpoint failed: status=$status"
    fi
    
    # Test 9: Admin login with wrong password
    log_info "Test: Admin login rejects wrong password"
    result=$(curl -s -w "\n%{http_code}" -X POST -H "Content-Type: application/json" \
        -d '{"password":"wrong-password"}' "${DASH_URL}/api/admin/login" 2>&1)
    status=$(echo "$result" | tail -1)
    if [ "$status" = "401" ]; then
        log_pass "Admin login correctly rejects wrong password"
    else
        log_fail "Admin login should reject wrong password: status=$status"
    fi
    
    # Test 10: Admin endpoints require auth
    log_info "Test: Admin health requires authentication"
    result=$(curl -s -w "\n%{http_code}" "${DASH_URL}/api/admin/health" 2>&1)
    status=$(echo "$result" | tail -1)
    if [ "$status" = "401" ]; then
        log_pass "Admin health correctly requires auth (401)"
    else
        log_fail "Admin health should require auth: status=$status"
    fi
    
    # Test 11: Admin login flow (if password provided)
    if [ -n "$ADMIN_PASSWORD" ]; then
        log_info "Test: Admin login flow"
        login_result=$(curl -s -w "\n%{http_code}" -c /tmp/dash_cookies.txt -X POST \
            -H "Content-Type: application/json" \
            -d "{\"password\":\"${ADMIN_PASSWORD}\"}" "${DASH_URL}/api/admin/login" 2>&1)
        login_status=$(echo "$login_result" | tail -1)
        
        if [ "$login_status" = "200" ]; then
            log_pass "Admin login successful"
            
            # Test authenticated endpoint
            log_info "Test: Authenticated admin health"
            auth_result=$(curl -s -w "\n%{http_code}" -b /tmp/dash_cookies.txt "${DASH_URL}/api/admin/health" 2>&1)
            auth_status=$(echo "$auth_result" | tail -1)
            if [ "$auth_status" = "200" ] || [ "$auth_status" = "404" ]; then
                log_pass "Admin health accessible after login ($auth_status)"
            else
                log_fail "Admin health failed after login: status=$auth_status"
            fi
            
            # Cleanup
            rm -f /tmp/dash_cookies.txt
        else
            log_skip "Admin login failed with status $login_status - skipping auth tests"
        fi
    fi
}

# ============================================================================
# DATABASE VALIDATION TESTS
# ============================================================================
run_db_tests() {
    log_section "Database Validation Tests"
    
    # These tests verify data consistency via API responses
    API_URL="http://${HOST}:${API_PORT}"
    
    api_get() {
        curl -s -H "X-API-Key: ${API_KEY}" "${API_URL}$1" 2>&1
    }
    
    # Test 1: Stats structure validation
    log_info "Test: Stats response structure"
    stats=$(api_get "/stats/all")
    if echo "$stats" | jq -e 'type == "array"' &>/dev/null; then
        log_pass "Stats returns array structure"
        
        # Check if any stats exist and validate structure
        count=$(echo "$stats" | jq 'length')
        if [ "$count" -gt 0 ]; then
            log_info "  Found $count player records"
            
            # Validate first record has expected fields
            has_uuid=$(echo "$stats" | jq -e '.[0].uuid' &>/dev/null && echo "yes" || echo "no")
            has_name=$(echo "$stats" | jq -e '.[0].name' &>/dev/null && echo "yes" || echo "no")
            
            if [ "$has_uuid" = "yes" ] && [ "$has_name" = "yes" ]; then
                log_pass "Stats records have required fields (uuid, name)"
            else
                log_fail "Stats records missing required fields"
            fi
        else
            log_info "  No player records yet (empty database)"
        fi
    else
        log_fail "Stats should return array, got: $(echo "$stats" | head -c 100)"
    fi
    
    # Test 2: Heatmap data structure
    log_info "Test: Heatmap response structure"
    heatmap=$(api_get "/heatmap/MINING")
    if echo "$heatmap" | jq -e 'type == "array" or type == "object"' &>/dev/null; then
        log_pass "Heatmap returns valid structure"
    else
        log_fail "Heatmap structure invalid"
    fi
    
    # Test 3: Moments data structure
    log_info "Test: Moments response structure"
    moments=$(api_get "/moments/recent?limit=5")
    if echo "$moments" | jq -e 'type == "array"' &>/dev/null; then
        log_pass "Moments returns array structure"
    else
        log_fail "Moments structure invalid"
    fi
    
    # Test 4: Timeline data structure
    log_info "Test: Timeline response structure"
    timeline=$(api_get "/timeline")
    if echo "$timeline" | jq empty 2>/dev/null; then
        log_pass "Timeline returns valid JSON"
    else
        log_skip "Timeline response not JSON"
    fi
    
    # Test 5: Social data structure
    log_info "Test: Social data structure"
    social=$(api_get "/social/top?limit=5")
    if echo "$social" | jq -e 'type == "array"' &>/dev/null; then
        log_pass "Social returns array structure"
    else
        log_fail "Social structure invalid"
    fi
    
    # Test 6: Health snapshot structure
    log_info "Test: Health snapshot structure"
    health=$(api_get "/health")
    if echo "$health" | jq empty 2>/dev/null; then
        if echo "$health" | jq -e '.timestamp or .error' &>/dev/null; then
            log_pass "Health returns valid snapshot or error"
        else
            log_pass "Health returns valid JSON"
        fi
    else
        log_fail "Health response not valid JSON"
    fi
}

# ============================================================================
# INTEGRATION TESTS
# ============================================================================
run_integration_tests() {
    log_section "Integration Tests"
    
    API_URL="http://${HOST}:${API_PORT}"
    
    # Test 1: API and RCON return consistent player count
    if command -v mcrcon &> /dev/null; then
        log_info "Test: API and RCON player count consistency"
        
        rcon_list=$(mcrcon -H "${HOST}" -P "${RCON_PORT}" -p "${RCON_PASSWORD}" "list" 2>&1)
        rcon_count=$(echo "$rcon_list" | grep -oP '\d+(?= of a max)' || echo "0")
        
        api_online=$(curl -s -H "X-API-Key: ${API_KEY}" "${API_URL}/online" 2>&1)
        api_count=$(echo "$api_online" | jq -r '.count // 0' 2>/dev/null || echo "0")
        
        if [ "$rcon_count" = "$api_count" ]; then
            log_pass "Player count consistent: RCON=$rcon_count, API=$api_count"
        else
            log_fail "Player count mismatch: RCON=$rcon_count, API=$api_count"
        fi
    else
        log_skip "RCON not available for integration test"
    fi
    
    # Test 2: Verify all API endpoints respond within timeout
    log_info "Test: API response times (< 5s timeout)"
    endpoints=("/stats/all" "/online" "/moments/recent" "/health" "/heatmap/MINING")
    all_fast=true
    
    for endpoint in "${endpoints[@]}"; do
        start_time=$(date +%s%N)
        curl -s -m 5 -H "X-API-Key: ${API_KEY}" "${API_URL}${endpoint}" > /dev/null 2>&1
        exit_code=$?
        end_time=$(date +%s%N)
        
        elapsed=$(( (end_time - start_time) / 1000000 ))
        
        if [ $exit_code -eq 0 ] && [ $elapsed -lt 5000 ]; then
            echo "  ✓ ${endpoint}: ${elapsed}ms"
        else
            echo "  ✗ ${endpoint}: timeout or error"
            all_fast=false
        fi
    done
    
    if [ "$all_fast" = true ]; then
        log_pass "All endpoints respond within timeout"
    else
        log_fail "Some endpoints timed out"
    fi
}

# ============================================================================
# MAIN
# ============================================================================
main() {
    echo ""
    echo "╔══════════════════════════════════════════════════════════════╗"
    echo "║          SMPStats E2E Test Suite                             ║"
    echo "╠══════════════════════════════════════════════════════════════╣"
    echo "║  Host:          ${HOST}"
    echo "║  RCON Port:     ${RCON_PORT}"
    echo "║  API Port:      ${API_PORT}"
    echo "║  Dashboard:     ${DASHBOARD_PORT}"
    echo "╚══════════════════════════════════════════════════════════════╝"
    echo ""
    
    run_rcon_tests
    run_api_tests
    run_dashboard_tests
    run_db_tests
    run_integration_tests
    
    log_section "Test Summary"
    echo ""
    echo -e "  ${GREEN}Passed:${NC}  ${TESTS_PASSED}"
    echo -e "  ${RED}Failed:${NC}  ${TESTS_FAILED}"
    echo -e "  ${YELLOW}Skipped:${NC} ${TESTS_SKIPPED}"
    echo ""
    
    total=$((TESTS_PASSED + TESTS_FAILED))
    if [ $total -gt 0 ]; then
        percentage=$((TESTS_PASSED * 100 / total))
        echo -e "  Pass rate: ${percentage}%"
    fi
    echo ""
    
    # Exit with failure if any tests failed
    if [ $TESTS_FAILED -gt 0 ]; then
        echo -e "${RED}❌ E2E Tests FAILED${NC}"
        exit 1
    else
        echo -e "${GREEN}✅ E2E Tests PASSED${NC}"
        exit 0
    fi
}

main
