# Commands & Permissions

## Commands

| Command | Description | Usage | Permission |
| :--- | :--- | :--- | :--- |
| `/map` | Map management commands | `/<command> <public\|private\|join\|leave> [args]` | `icedspear.map` |
| `/party` | Party management commands | `/<command> <create\|join\|leave\|map\|list> [args]` | `icedspear.party` |
| `/friend` | Friend management commands | `/<command> <add\|accept\|remove\|list\|requests> [args]` | `icedspear.friend` |
| `/icedspear` | Admin commands | `/<command> <import\|list\|reload\|config\|block\|unblock> [args]` | `icedspear.admin` |
| `/leaderboard` | View map leaderboards | `/<command> [mapname]` | `icedspear.leaderboard` |

## Permissions

| Permission | Description | Default |
| :--- | :--- | :--- |
| `icedspear.admin` | Access to admin commands | `op` |
| `icedspear.bypass` | Bypass all map restrictions | `op` |
| `icedspear.map` | Access to map commands | `true` |
| `icedspear.map.public` | Create public maps | `true` |
| `icedspear.map.private` | Create private maps | `true` |
| `icedspear.map.*` | Access to all maps | `op` |
| `icedspear.party` | Access to party commands | `true` |
| `icedspear.friend` | Access to friend commands | `true` |
| `icedspear.leaderboard` | View leaderboards | `true` |
