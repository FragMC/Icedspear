# Features

IcedSpear is packed with features designed to make lobby and map management effortless.

## Map Management

The core of IcedSpear is its dynamic map instance system. Unlike standard multi-world plugins, IcedSpear treats maps as temporary or persistent instances that can be created on demand.

### Instance Types
- **Public Maps**: Shared instances accessible by anyone via the map selector or commands. Great for main lobbies or open game modes.
    - ID Format: `PUBLIC_<mapName>`
- **Private Maps**: Exclusive instances created for specific players or groups.
    - ID Format: `<mapName>_<randomCode>`
- **Party Maps**: Private instances specifically reserved for a party.
    - ID Format: `<mapName>_party-<partyCode>`

### Lifecycle
1.  **Creation**: When a map is requested, IcedSpear asynchronously generates a new world using a lightweight void generator.
2.  **Initialization**: Schematics are pasted (if configured) and game rules are applied.
3.  **Active**: Players play in the instance.
4.  **Cleanup**: When the last player leaves (plus a configurable delay), the world is unloaded and deleted to save resources.

## Party System

IcedSpear includes a fully-featured party system to keep friends together.

### Capabilities
- **Creation**: Any player can create a party and become the leader.
- **Invites**: Leaders can invite other players via `/party invite <player>`.
- **Warping**: When the party leader joins a map, all party members are automatically warped to that same map instance.
- **Chat**: A dedicated party chat channel (if enabled).

### Commands
- `/party create`: Start a new party.
- `/party invite <player>`: Send an invitation.
- `/party join <code>` or `/party accept <player>`: Join a party.
- `/party leave`: Leave your current party.

## Friend System

Build a community with the integrated friend system.

- **Friend Requests**: Send and receive friend requests.
- **Online Status**: See which friends are online and what map they are playing on.
- **Quick Actions**: Easily invite friends to your party.

## Leaderboards

Foster competition with built-in leaderboards.

- **Per-Map Tracking**: Track fastest times, high scores, or other metrics for each specific map.
- **Global & Instance**: View global top players or just the leaders in your current instance.
- **Holograms**: (Optional) Display leaderboards in-game using hologram integrations.
