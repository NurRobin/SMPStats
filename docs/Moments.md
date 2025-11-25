# Moments Konfiguration (Cheatsheet)

Moments sind über `moments.definitions` in `config.yml` konfigurierbar. Jede Definition hat:

- `type` (required): einer von
  - `block_break` – löst bei Block-Abbau aus
  - `death` – löst bei Tod aus
  - `death_fall` – Tod durch Fallschaden (optional `min_fall_distance`)
  - `first_death` – nur beim ersten Tod (pro Spieler)
  - `damage_low_hp` – nach Schaden, wenn HP unter Schwellwert fällt
  - `death_explosion` – Tod durch Explosion, optional nur Self
- `title` (required): Text, Platzhalter: `{player}`, `{count}`, `{fall}`, `{health}`, `{cause}`
- `detail` (optional): weiterer Text mit denselben Platzhaltern
- `merge_seconds` (optional, default 0): Fenster zum Mergen wiederholter Events (z. B. Diamanten-Run)
- `first_only` (optional): speichert nur einmal pro Spieler (nur sinnvoll bei `first_death`)
- `materials` (optional): Materialliste (nur `block_break`)
- `min_fall_distance` (optional): Mindest-Falldistanz (nur `death_fall`)
- `max_health_after_damage` (optional): Max-HP nach Treffer (nur `damage_low_hp`)
- `causes` (optional): Liste von `DamageCause`-Strings (z. B. `FALL`, `BLOCK_EXPLOSION`)
- `require_self` (optional): true = nur wenn Spieler selbst Verursacher (nur `death_explosion`)

## Beispiel
```yaml
moments:
  enabled: true
  flush_seconds: 10
  definitions:
    diamond_run:
      type: block_break
      title: "Diamanten Run"
      detail: "Diamanten gefunden: {count}"
      merge_seconds: 30
      materials: [DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE]
    clutch_low_hp:
      type: damage_low_hp
      title: "Clutch"
      detail: "{player} überlebt mit {health} HP."
      max_health_after_damage: 1.0
    tnt_self:
      type: death_explosion
      title: "Self TNT"
      detail: "{player} hat sich selbst in die Luft gesprengt."
      causes: [BLOCK_EXPLOSION, ENTITY_EXPLOSION]
      require_self: true
```
