# IcedSpear Documentation

IcedSpear is a custom map lobbies plugin with a built-in party and friend system. It allows players to create private or public instances of maps, play with friends, and manage parties.

## Requirements
- **Java**: 21+
- **Server**: Paper/Spigot 1.21+
- **Dependencies**: FastAsyncWorldEdit (FAWE)

## Features
- **Map Instances**: Create private or public instances of maps.
- **Schematic Support**: Maps are loaded from schematics using FAWE.
- **Party System**: Create parties, invite friends, and warp the entire party to a map.
- **Friend System**: Add friends to easily invite them to parties.
- **Void Worlds**: Map instances are created in optimized void worlds.
- **Configuration**: Highly configurable map settings and restrictions.

## Commands

### Map Commands
Base command: `/map`
- `/map public <mapName>`: Create/Join a public instance of a map.
- `/map private <mapName>`: Create a private instance of a map.
- `/map join <instanceId>`: Join a specific map instance.
- `/map leave`: Leave the current map instance.

### Party Commands
Base command: `/party`
- `/party create`: Create a new party.
- `/party invite <player>`: Invite a player to your party.
- `/party join <player>`: Request to join a friend's party.
- `/party leave`: Leave your current party.
- `/party list`: List members in your party.
- `/party kick <player>`: Kick a player from the party (Leader only).
- `/party chat`: Toggle party chat.

### Friend Commands
Base command: `/friend`
- `/friend add <player>`: Send a friend request.
- `/friend accept <player>`: Accept a friend request.
- `/friend remove <player>`: Remove a friend.
- `/friend list`: List your friends.
- `/friend requests`: List pending friend requests.

### Admin Commands
Base command: `/icedspear` (Permission: `icedspear.admin`)
- `/icedspear import <worldEditName> <targetName>`: Import a schematic from WorldEdit/FAWE.
- `/icedspear list`: List all active map instances.
- `/icedspear reload`: Reload the configuration.
- `/icedspear config <setting> <value>`: Modify configuration values.
- `/icedspear block <mapName> [player]`: Block a map globally or for a specific player.
- `/icedspear unblock <mapName> [player]`: Unblock a map.

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `icedspear.admin` | Access to all admin commands. | OP |
| `icedspear.bypass` | Bypass map restrictions (blocked maps). | OP |
| `icedspear.map` | Access to basic map commands. | True |
| `icedspear.map.public` | Permission to create public maps. | True |
| `icedspear.map.private` | Permission to create private maps. | True |
| `icedspear.party` | Access to party commands. | True |
| `icedspear.friend` | Access to friend commands. | True |

## Configuration

The `config.yml` file allows you to customize the plugin's behavior.

### Key Settings
- **max-players**: Maximum number of players per map instance (default: 10).
- **cleanup-delay-seconds**: Time in seconds to keep an empty private map before deleting it (default: 15).
- **map-data-url**: URL to a JSON file containing map definitions.
  - Format: `{"mapName": {"schematic": "schematicFile"}, ...}`
- **globally-blocked-maps**: List of maps that are disabled for everyone (except those with bypass permission).

### Example JSON Data (map-data-url)
```json
{
  "SkyWars": {
    "schematic": "skywars_lobby"
  },
  "BedWars": {
    "schematic": "bedwars_lobby"
  }
}
```
