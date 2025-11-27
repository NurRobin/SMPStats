# SMPStats Permissions

This document outlines all permissions available in SMPStats and their intended use cases.

## Permission Hierarchy

SMPStats uses a hierarchical permission system. The `smpstats.admin` permission grants access to all other permissions.

## Command Permissions

| Permission | Default | Description |
|------------|---------|-------------|
| `smpstats.use` | `true` | Access to `/sstats` and `/smpstats` commands |
| `smpstats.reload` | `op` | Reload plugin configuration (`/sstats reload`) |
| `smpstats.edit` | `op` | Set/reset player statistics (`/sstats user <name> set/reset`) |

## GUI Permissions

| Permission | Default | Description |
|------------|---------|-------------|
| `smpstats.gui` | `true` | Access to the GUI menu (`/sstats gui`) |
| `smpstats.gui.stats` | `true` | View own statistics in the GUI |
| `smpstats.gui.stats.others` | `op` | View other players' statistics in the GUI |
| `smpstats.gui.leaderboard` | `true` | Access leaderboards in the GUI |
| `smpstats.gui.health` | `op` | Access Server Health monitoring in the GUI |
| `smpstats.gui.health.manage` | `op` | Kill entities and teleport to locations via the GUI |

## Legacy Permissions

These permissions are maintained for backward compatibility:

| Permission | Default | Description |
|------------|---------|-------------|
| `smpstats.health` | `op` | Access Server Health stats (use `smpstats.gui.health` instead) |

## Admin Permission

| Permission | Default | Description |
|------------|---------|-------------|
| `smpstats.admin` | `op` | Grants all SMPStats permissions |

The `smpstats.admin` permission includes:
- `smpstats.reload`
- `smpstats.edit`
- `smpstats.gui`
- `smpstats.gui.stats`
- `smpstats.gui.stats.others`
- `smpstats.gui.leaderboard`
- `smpstats.gui.health`
- `smpstats.gui.health.manage`
- `smpstats.health`

## Example Configurations

### LuckPerms

```yaml
# Give a player basic GUI access
/lp user <player> permission set smpstats.gui true

# Give a moderator health monitoring access
/lp group moderator permission set smpstats.gui.health true

# Give admins full access
/lp group admin permission set smpstats.admin true
```

### permissions.yml (Bukkit)

```yaml
groups:
  default:
    permissions:
      - smpstats.use
      - smpstats.gui
      - smpstats.gui.stats
      - smpstats.gui.leaderboard
  
  moderator:
    permissions:
      - smpstats.gui.health
  
  admin:
    permissions:
      - smpstats.admin
```

## Permission Nodes by Feature

### Statistics Viewing
- **Own stats**: `smpstats.gui.stats`
- **Other players' stats**: `smpstats.gui.stats.others`
- **Leaderboards**: `smpstats.gui.leaderboard`

### Server Health Monitoring
- **View health metrics**: `smpstats.gui.health`
- **View charts & history**: `smpstats.gui.health`
- **Entity breakdown**: `smpstats.gui.health`
- **Kill entities**: `smpstats.gui.health.manage`
- **Teleport to entities**: `smpstats.gui.health.manage`
- **Teleport to hot chunks**: `smpstats.gui.health.manage`

### Administration
- **Reload config**: `smpstats.reload`
- **Edit player stats**: `smpstats.edit`

## Notes

1. **Defaults**: Permissions with `default: true` are granted to all players by default. Permissions with `default: op` require operator status or explicit permission grants.

2. **GUI Indicators**: The GUI shows visual indicators when a player lacks permission for a feature. Items will display "Requires Permission" in red when access is denied.

3. **Backward Compatibility**: The legacy `smpstats.health` permission is still checked alongside `smpstats.gui.health` for backward compatibility. Both permissions grant the same access.
