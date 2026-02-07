# Configuration

IcedSpear is highly configurable. This guide covers the main configuration files.

## `config.yml`

The main configuration file handles global settings, permissions, and map defaults.

```yaml
# IcedSpear Main Configuration

# URL to fetch the map data JSON (see below)
map-data-url: "https://example.com/maps/icedspear.json"

# Maximum players allowed in a single map instance
max-players: 10

# Time (in seconds) to wait before deleting an empty map
cleanup-delay: 60

# Default gamemode for players joining a map (SURVIVAL, ADVENTURE, CREATIVE)
default-gamemode: ADVENTURE

# World GameRules applied to every map instance
world-gamerules:
  doDaylightCycle: false
  doWeatherCycle: false
  doMobSpawning: false
  keepInventory: true

# Globally blocked maps (players cannot join these)
globally-blocked-maps:
  "debug_map": true

# Player-specific blocks (managed via commands)
blocked-maps: {}
```

## Map Data JSON (`icedspear.json`)

IcedSpear can fetch map definitions from an external JSON URL. This allows you to update the available map list without restarting the server or editing local files.

Configure the URL in `config.yml` under `map-data-url`.

### Structure

The JSON structure maps a **display name** (used in commands) to a **schematic file name** (stored in the `schematics/` folder).

```json
{
  "MapName": {
    "schematic": "filename_without_extension"
  },
  "AnotherMap": {
    "schematic": "another_schematic"
  }
}
```

### Example `icedspear.json`

```json
{
  "Tutorial": {
    "schematic": "tutorial_v2"
  },
  "IceCourse": {
    "schematic": "ice_course_final"
  },
  "LavaRun": {
    "schematic": "lava_run_1"
  }
}
```

*   **Key (`"Tutorial"`)**: The name players use in-game (e.g., `/map join Tutorial`).
*   **Value (`"schematic"`)**: The name of the schematic file located in `plugins/IcedSpear/schematics/`.
    *   Do not include `.schem` or `.schematic` in the JSON value.
    *   The plugin will look for `tutorial_v2.schem` or `tutorial_v2.schematic`.

## Schematic Management

Schematics are stored in `plugins/IcedSpear/schematics/`.

*   **Importing**: You can import schematics from WorldEdit or FastAsyncWorldEdit using `/icedspear import <name>`.
*   **Format**: Supports standard Sponge `.schem` format (recommended) and legacy `.schematic`.
