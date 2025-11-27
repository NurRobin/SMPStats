# CI/CD Deployment & Runtime Testing

This document describes the CI/CD workflows for automated deployment and runtime testing of SMPStats.

---

## Overview

SMPStats has two main CI/CD features for development and testing:

1. **Dev Deployment** - Automatically deploy builds to a dev/test Minecraft server
2. **End-to-End Runtime Tests** - Comprehensive tests with a real Paper server

---

## 1. Dev Deployment Workflow

### Purpose

Automatically build and deploy SMPStats to a dedicated dev/test server whenever changes are pushed to `dev` or `feature/**` branches.

### Workflow File

`.github/workflows/deploy-dev.yml`

### Triggers

- Push to `dev` branch
- Push to `feature/**` branches
- Manual trigger via `workflow_dispatch`

### What It Does

1. **Build** - Runs `mvn verify` to compile and test the plugin
2. **Backup** - Creates a timestamped backup of the existing plugin on the server
3. **Upload** - Uploads the new JAR to the server's plugins directory via SCP
4. **Reload** - Uses RCON to announce deployment and reload the plugin
5. **Log Capture** - Captures recent log messages for debugging

### Required Secrets

Configure these in your repository settings (Settings → Secrets and variables → Actions):

| Secret | Description | Example |
|--------|-------------|---------|
| `DEV_SERVER_HOST` | SSH hostname or IP | `dev.example.com` |
| `DEV_SERVER_USER` | SSH username | `minecraft` |
| `DEV_SERVER_SSH_KEY` | Private SSH key (OpenSSH format) | `-----BEGIN OPENSSH...` |
| `DEV_SERVER_SSH_PORT` | SSH port (optional, default: 22) | `22` |
| `DEV_PLUGINS_DIR` | Path to plugins directory (optional) | `/opt/minecraft/paper-dev/plugins` |
| `DEV_RCON_HOST` | RCON hostname (optional, default: localhost) | `localhost` |
| `DEV_RCON_PORT` | RCON port (optional, default: 25575) | `25575` |
| `DEV_RCON_PASSWORD` | RCON password | `secure-password` |

### Server Requirements

The dev server needs:

- **SSH access** with key-based authentication
- **mcrcon** installed for RCON commands (`apt install mcrcon` or build from [Tiiffi/mcrcon](https://github.com/Tiiffi/mcrcon))
- **RCON enabled** in `server.properties`:
  ```properties
  enable-rcon=true
  rcon.port=25575
  rcon.password=your-secure-password
  ```
- Optionally, [PlugMan](https://www.spigotmc.org/resources/plugman.93073/) for safer plugin reloads

### Environment

Create a GitHub Environment named `dev-server` for additional protection rules:

1. Go to Settings → Environments → New environment
2. Name it `dev-server`
3. (Optional) Add required reviewers or deployment branches

---

## 2. End-to-End Runtime Tests

### Purpose

Run comprehensive tests against a real Paper server to validate plugin functionality, RCON commands, and HTTP API endpoints.

### Workflow File

`.github/workflows/e2e-runtime-test.yml`

### Triggers

- Pull requests to `main` or `dev`
- Manual trigger via `workflow_dispatch`

### What It Does

1. **Build** - Compiles the plugin
2. **Setup Server** - Downloads Paper, configures server properties and plugin config
3. **Start Server** - Launches Paper with the plugin loaded
4. **RCON Tests** - Executes commands via RCON and validates responses
5. **HTTP API Tests** - Calls API endpoints and validates:
   - Authentication (rejects unauthenticated requests)
   - Status codes (200, 400, 404)
   - JSON response validity
6. **Cleanup** - Stops the server and uploads logs as artifacts

### Manual Trigger Options

When triggering manually, you can configure:

| Input | Description | Default |
|-------|-------------|---------|
| `mc_version` | Minecraft server version | `1.21.1` |
| `run_api_tests` | Run HTTP API tests | `true` |
| `run_rcon_tests` | Run RCON command tests | `true` |

### Test Coverage

#### RCON Command Tests

- `/list` - Basic server connectivity
- `/smpstats info` - Plugin info command
- `/stats` - Player stats command

#### HTTP API Tests

| Endpoint | Expected | Validated |
|----------|----------|-----------|
| `GET /stats/all` (no auth) | 401 | ✅ |
| `GET /stats/all` | 200 + JSON | ✅ |
| `GET /online` | 200 + JSON | ✅ |
| `GET /moments/recent` | 200 + JSON | ✅ |
| `GET /health` | 200 + JSON | ✅ |
| `GET /stats/{invalid-uuid}` | 400/404 | ✅ |
| `GET /heatmap/MINING` | 200 + JSON | ✅ |

### Artifacts

On completion, the workflow uploads:

- Server logs (`logs/latest.log`)
- Plugin data directory (`plugins/SMPStats/`)

Artifacts are retained for 3 days.

---

## 3. Existing Runtime Tests

### Basic Runtime Test

`.github/workflows/runtime-test.yml`

A simpler test that validates the plugin loads correctly on multiple Paper versions (1.21.1, 1.21.10). This runs on every push and PR.

### Snapshot Build

`.github/workflows/snapshot.yml`

Creates snapshot artifacts for `dev` and feature branches, useful for manual testing.

---

## Adding New Runtime Tests

### Adding RCON Tests

Edit `e2e-runtime-test.yml` and add commands in the "Run RCON command tests" step:

```yaml
- name: Run RCON command tests
  run: |
    # Add new test
    echo "Testing /mycommand..."
    RESULT=$(mcrcon -H localhost -P ${{ env.RCON_PORT }} -p "${{ env.RCON_PASSWORD }}" "mycommand" 2>&1)
    echo "Result: $RESULT"
    
    if echo "$RESULT" | grep -q "expected output"; then
      echo "✅ Test passed!"
    else
      echo "❌ Test failed!"
      exit 1
    fi
```

### Adding API Tests

Add calls to the `test_endpoint` function:

```yaml
test_endpoint "GET /new-endpoint" "/new-endpoint" "200"
test_endpoint "POST not allowed" "/stats/all" "405"
```

### Using Test Fixtures

For tests requiring specific database state, you can:

1. Create a pre-seeded SQLite database in `.github/test-fixtures/`
2. Copy it to the server before starting:

```yaml
- name: Seed test database
  run: |
    cp .github/test-fixtures/test-stats.db test-server/plugins/SMPStats/stats.db
```

---

## Troubleshooting

### Deployment Fails

1. Check SSH connectivity: Can you manually SSH from the Actions runner?
2. Verify secrets are set correctly (check for trailing whitespace)
3. Check server permissions: Can the SSH user write to the plugins directory?

### RCON Not Working

1. Ensure RCON is enabled in `server.properties`
2. Verify `mcrcon` is installed on the server
3. Check RCON password matches

### API Tests Fail

1. Check the API is enabled in `config.yml`
2. Verify the bind address allows connections from localhost
3. Check the API key matches

### Server Fails to Start

1. Check the uploaded logs artifact
2. Verify Java version compatibility
3. Ensure enough memory is available (workflow uses 1GB heap)

---

## Security Notes

- Never commit secrets to the repository
- Use GitHub Secrets for all sensitive data
- RCON passwords should be unique and secure
- Consider IP restrictions on the dev server
- The dev environment should never contain production data

---

## Related Documentation

- [RELEASE_PROCESS.md](./RELEASE_PROCESS.md) - How releases are created
- [AUTO_RELEASE_SUMMARY.md](./AUTO_RELEASE_SUMMARY.md) - Automated release system overview
- [API.md](./API.md) - HTTP API documentation
