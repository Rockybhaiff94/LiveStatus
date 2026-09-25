# LiveStatus - Administrator & Command Reference

> **You only need one Paper plugin JAR.**
>
> All administrative commands, storage, and YouTube queries run directly within `LiveStatus-1.0.0.jar`.

---

## 1. User Roles & Authorization Concept

There are two primary user roles:
1. **Server Administrator**: Controls which players are authorized to connect their YouTube account.
2. **Authorized Player**: Content creators permitted to link their personal YouTube channel.

> [!IMPORTANT]
> A standard player **cannot** link a YouTube account without explicit administrative permission. Having the `livestatus.link` permission alone is not sufficient; the administrator must explicitly execute `/youtube allow-link <player>` (or `/livestatus allow-link <player>`).

---

## 2. Command Reference

### Administrative Commands (`/youtube` or aliases `/yt`, `/livestatus`)

| Command | Permission | Description |
|---|---|---|
| `/youtube allow-link <player>` | `livestatus.admin` | Authorizes the specified player to link their YouTube account. |
| `/youtube revoke-link <player>` | `livestatus.admin` | Revokes linking authorization for the specified player. |
| `/youtube status <player>` | `livestatus.admin` | Displays real-time status: link state, channel details, active broadcast, game detection, and error logs. |
| `/youtube unlink <player>` | `livestatus.admin` | Forcefully unlinks the player's YouTube account and removes their LIVE nametag. |
| `/youtube reload` | `livestatus.admin` | Safely reloads `config.yml`, `allowed-players.yml`, and storage without interrupting server uptime. |
| `/youtube help` | `livestatus.admin` | Displays available administrative commands. |

### Player Commands (`/link`)

| Command | Permission | Description |
|---|---|---|
| `/link youtube` | `livestatus.link` | Requests a one-time secure OAuth URL to link the player's YouTube account (requires admin authorization). |
| `/link youtube unlink` | `livestatus.link` | Unlinks the player's currently connected YouTube account. |

---

## 3. Permissions

| Permission Node | Default | Description |
|---|---|---|
| `livestatus.admin` | `op` | Grants access to all `/youtube` and `/livestatus` subcommands. |
| `livestatus.link` | `true` | Allows execution of `/link youtube` (subject to admin authorization). |
| `livestatus.bypass` | `false` | Administrative bypass node for future permission overrides. |
| `youtubelivetag.admin` | `op` | Legacy alias for `livestatus.admin`. |
| `youtubelivetag.link` | `true` | Legacy alias for `livestatus.link`. |

---

## 4. End-to-End Administrator Workflow

### Step 1: Authorizing a Streamer
When a content creator joins your server (e.g. `PokeHowlet`), authorize them:
```
/youtube allow-link PokeHowlet
```
* The plugin persistently stores `PokeHowlet`'s Minecraft UUID in `allowed-players.yml`.
* If `PokeHowlet` is currently online, they receive an immediate in-game chat notification informing them that they are now authorized.

### Step 2: Player Links YouTube
The player executes in chat:
```
/link youtube
```
* The plugin generates a secure, one-time OAuth link bound to the player's UUID.
* A clickable message appears in chat:
  ```text
  [LiveStatus]
  Click here to connect your YouTube account:
  [ CONNECT YOUTUBE ]
  This link expires in 5 minutes.
  ```
* The player clicks the link, opens Google OAuth in their browser, and grants read-only access.
* The plugin's embedded callback receiver captures the response, exchanges the code for tokens, and notifies the player in-game.

### Step 3: Checking Streamer Status
Administrators can inspect live status at any time:
```
/youtube status PokeHowlet
```
Example Output:
```
--- LiveStatus: PokeHowlet ---
Admin Authorized: YES
Account Linked: YES
Channel: PokeHowlet Official
Live Status: Live Minecraft
Stream Title: Minecraft 1.21 Hardcore Survival
Game Detection: Verified Minecraft
Last Checked: 2026-09-24T09:00:00Z
```

### Step 4: Revoking Authorization or Unlinking
If a creator leaves the server staff or creator program:
- To prevent them from re-linking:
  ```
  /youtube revoke-link PokeHowlet
  ```
- To forcefully disconnect their YouTube account and immediately strip any active LIVE nametag:
  ```
  /youtube unlink PokeHowlet
  ```
