# WebLink Addon

**WebLink** is a powerful IcedSpear addon that bridges your Minecraft server with your website. It enables secure account linking, real-time data fetching, and remote command execution.

## Features

*   **Secure Account Linking**: Players link their Minecraft account to a website account using a temporary 6-digit code.
*   **Webhook API**: A built-in HTTP server exposes endpoints for your website to query player data.
*   **Remote Control**: Execute console commands via secure webhooks.
*   **SQLite Storage**: Fast, local storage for link data (no MySQL required).

---

## Installation

1.  Place `WebLink.jar` in your server's `plugins/` folder.
2.  Ensure **IcedSpear** is installed.
3.  Restart the server.
4.  Configure the `webhook-port` and `cors-origin` in `config.yml` if needed.

---

## Commands

### Player Commands

| Command | Permission | Description |
| :--- | :--- | :--- |
| `/weblink link` | None | Generates a 6-digit code to link your account. |
| `/weblink unlink` | None | Unlinks your current account. |
| `/weblink status` | None | Checks if your account is currently linked. |

---

## Configuration

The `config.yml` for WebLink is simple:

```yaml
# Port for the internal HTTP server
webhook-port: 25531

# CORS Origin for web requests (use "*" for dev, your domain for prod)
cors-origin: "https://yourwebsite.com"

# Secret key for signing/verifying webhook requests (keep this safe!)
webhook-secret: "change-this-to-a-secure-random-string"
```

---

## Webhook API

WebLink runs a local HTTP server (default port `25531`) that accepts JSON requests.

### Authentication
(Implementation dependent - typically requires a shared secret or token signature).

### Endpoints

#### `POST /webhook/check-link`
Checks if a website account ID is linked to any Minecraft players.
*   **Input**: `{"accid": "hashed_account_id"}`
*   **Output**:
    ```json
    {
      "success": true,
      "linked": true,
      "accounts": [
        { "uuid": "...", "username": "PlayerName", "online": true }
      ]
    }
    ```

#### `POST /webhook/verify-code`
Verifies a 6-digit link code entered by a user on the website.
*   **Input**: `{"code": "123456", "accid": "hashed_account_id"}`
*   **Output**: `{"success": true, "uuid": "...", "username": "..."}`

#### `POST /webhook/execute-command`
Executes a console command.
*   **Input**: `{"command": "say Hello World"}`
*   **Output**: `{"success": true, "output": "..."}`

#### `POST /webhook/check-online`
Checks if a specific player is online.
*   **Input**: `{"uuid": "..."}`
*   **Output**: `{"online": true}`

#### `GET /webhook/get-friends?uuid=...`
Returns the friend list of a player (via IcedSpear API).

#### `GET /webhook/get-party?uuid=...`
Returns the current party information of a player.

#### `GET /webhook/get-map?uuid=...`
Returns the current map instance a player is in.

#### `GET /webhook/get-available-maps`
Returns a list of all available maps defined in IcedSpear.

---

## Database Schema

WebLink uses a local SQLite database (`linked_accounts.db`) located in the plugin folder.

### Table: `linked_accounts`

| Column | Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `uuid` | `TEXT` | `UNIQUE NOT NULL` | Minecraft Player UUID. |
| `accid` | `TEXT` | `NOT NULL` | Hashed Website Account ID. |

*   **Relationship**: One-to-Many (One website account can be linked to multiple Minecraft accounts, but one Minecraft account can only be linked to one website account).
