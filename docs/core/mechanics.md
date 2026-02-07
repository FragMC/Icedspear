# Gameplay Mechanics

IcedSpear introduces several gameplay mechanics that automate map management and enhance the player experience.

## Map Lifecycle

IcedSpear manages maps through a state-based lifecycle to ensure smooth gameplay and resource efficiency.

1.  **Creation**: When a player requests a map (or a party leader warps their party), IcedSpear creates a new temporary world.
2.  **Preparation**:
    - The map's schematic is pasted into the world.
    - The plugin scans for a **Gold Block** to determine the spawn point.
3.  **Waiting**: Players are teleported to the map and held in a waiting state until the map is fully ready.
4.  **Running**: Once players are in position, the game begins. The timer starts when they move.
5.  **Cleanup**: When all players leave, the world is unloaded and deleted to free up server resources.

## Special Blocks

Schematic designers can use specific blocks to define gameplay elements. These blocks are detected automatically when the map is loaded or during gameplay.

### Gold Block (Spawn Point)

- **Function**: Defines the starting spawn location for the map.
- **Usage**: Place a single Gold Block in your schematic where you want players to spawn.
- **Behavior**:
  - On map load, the plugin scans the schematic bounds for `Material.GOLD_BLOCK`.
  - The spawn point is set to the center of the top of this block.
  - If no Gold Block is found, the spawn defaults to `0, 100, 0` (plus an offset).

### Diamond Block (Finish Line)

- **Function**: Marks the end of the map.
- **Usage**: Place Diamond Blocks at the end of the course.
- **Behavior**:
  - When a player steps on a Diamond Block (specifically, when the block directly _under_ them is `Material.DIAMOND_BLOCK`), the run is considered finished.
  - **Timer Stop**: The player's timer is stopped immediately, and their time is recorded.
  - **Flight Mode**: The player is automatically given flight capabilities (`/fly`) to spectate others or explore.
  - **Completion Message**: A "FINISHED!" message is displayed with the final time.

## Timer System

IcedSpear includes a built-in precision timer for competitive gameplay.

- **Start**: The timer begins automatically when a player moves from their spawn position.
- **Tracking**: Time is tracked in milliseconds.
- **Display**: An action bar displays the current run time in real-time.
- **Leaderboards**: Upon finishing, the time is compared against the map's leaderboard (if enabled).

## Game Rules

By default, map instances are configured for parkour/adventure gameplay:

- **Daylight Cycle**: Frozen (default: midnight/noon depending on config).
- **Weather**: Locked (no rain/storm).
- **Mob Spawning**: Disabled.
- **Gamemode**: Adventure/Survival (configurable).
