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

### Streamer Chat Announcement Commands (`/youtube chat`)

| Command | Permission | Description |
|---|---|---|
| `/youtube chat` | `youtubelivetag.chat` | Displays help for streamer live chat announcement settings. |
| `/youtube chat status` | `youtubelivetag.chat` | Shows your current announcement settings, cooldown, live stream, and effective title. |
| `/youtube chat on` | `youtubelivetag.chat` | Enables automatic live stream chat announcements for your streams. |
| `/youtube chat off` | `youtubelivetag.chat` | Disables announcements for your streams (requires admin permission if forced). |
| `/youtube chat title <text>` | `youtubelivetag.chat` | Sets a custom announcement message/title displayed when your stream is announced. |
| `/youtube chat title clear` | `youtubelivetag.chat` | Clears custom announcement title; resumes using dynamic YouTube stream title. |
| `/youtube chat interval <minutes>` | `youtubelivetag.chat` | Sets a custom re-announcement interval (enforces server minimum and maximum). |
| `/youtube chat interval random` | `youtubelivetag.chat` | Switches to randomized interval mode (generates a new cooldown after each announcement). |
| `/youtube chat interval default` | `youtubelivetag.chat` | Resets announcement interval to server default. |

### Administrator Chat Announcement Controls (`/youtube chat admin`)

| Command | Permission | Description |
|---|---|---|
| `/youtube chat admin status` | `youtubelivetag.chat.admin` | Displays live statistics: authorized, linked, active streamers, scheduler status, ETA, and sent counts. |
| `/youtube chat admin on` | `youtubelivetag.chat.admin` | Globally enables the chat announcement system. |
| `/youtube chat admin off` | `youtubelivetag.chat.admin` | Globally disables all chat announcements on the server. |
| `/youtube chat admin enable <player>` | `youtubelivetag.chat.admin` | Administratively enables announcements for the specified player. |
| `/youtube chat admin disable <player>` | `youtubelivetag.chat.admin` | Administratively disables announcements for the specified player (admin override). |
| `/youtube chat admin interval <player> <minutes\|random\|default>` | `youtubelivetag.chat.admin` | Configures announcement interval mode and cooldown for the specified player. |
| `/youtube chat admin title <player> <text>` | `youtubelivetag.chat.admin` | Sets custom announcement title for the specified player. |
| `/youtube chat admin title clear <player>` | `youtubelivetag.chat.admin` | Clears custom announcement title for the specified player. |

### Player Linking Commands (`/link`)

| Command | Permission | Description |
|---|---|---|
| `/link youtube` | `livestatus.link` | Requests a one-time secure OAuth URL to link the player's YouTube account (requires admin authorization). |
| `/link youtube unlink` | `livestatus.link` | Unlinks the player's currently connected YouTube account. |

---

## 3. Permissions

| Permission Node | Default | Description |
|---|---|---|
| `livestatus.admin` | `op` | Grants access to all administrative commands. |
| `youtubelivetag.admin` | `op` | Compatibility alias for `livestatus.admin`. |
| `livestatus.link` | `true` | Allows execution of `/link youtube` (subject to admin authorization). |
| `youtubelivetag.link` | `true` | Compatibility alias for `livestatus.link`. |
| `youtubelivetag.chat` | `true` | Allows an authorized streamer to configure their own live chat announcements. |
| `livestatus.chat` | `true` | Alias for `youtubelivetag.chat`. |
| `youtubelivetag.chat.admin` | `op` | Allows administrators to control global and per-player announcement settings. |
| `livestatus.chat.admin` | `op` | Alias for `youtubelivetag.chat.admin`. |
| `youtubelivetag.chat.bypass` | `false` | Players with this permission will not receive automated live chat announcements. |
| `livestatus.chat.bypass` | `false` | Alias for `youtubelivetag.chat.bypass`. |

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

---

## 5. Live Stream Chat Announcement System

### Overview
LiveStatus includes a high-performance, centralized live stream chat announcement engine.
- **Single Centralized Scheduler**: Exactly one repeating task evaluates all eligible streamers.
- **Zero Additional API Requests**: Reuses the existing YouTube live detection cache.
- **Fair Randomized Rotation**: If multiple streamers are live, longer-waiting streamers are weighted with higher selection probability, avoiding predictable loops and preventing starvation.
- **Independent Cooldowns**: Streamer interval timers are independent minimum cooldowns. A long cooldown on one streamer never delays other eligible streamers.
- **Global Anti-Spam Gap**: Enforces a configurable global gap (default 60 seconds) between any announcements on the server.
- **New Stream Recognition**: Changing YouTube video ID resets cooldown immediately so new streams are announced right away.
- **Rich Adventure Clickable Chat**: Displays a clickable `[WATCH STREAM]` button opening the exact verified YouTube URL with hover tooltip.
- **Injection Protection**: Streamer titles and stream titles are treated strictly as data and cannot inject MiniMessage markup or commands.

### Streamer Workflow
1. Once authorized and linked, the streamer runs:
   ```
   /youtube chat on
   ```
2. (Optional) Set a custom title:
   ```
   /youtube chat title We are going to dominate this SMP!
   ```
3. (Optional) Choose an announcement cooldown mode:
   ```
   /youtube chat interval 10         # Custom 10-minute cooldown
   /youtube chat interval random     # Randomized interval between min and max
   /youtube chat interval default    # Server default (15 minutes)
   ```
4. Check current status:
   ```
   /youtube chat status
   ```

### Administrator Controls
- Inspect real-time announcement system statistics:
  ```
  /youtube chat admin status
  ```
- Toggle global announcements on or off:
  ```
  /youtube chat admin on
  /youtube chat admin off
  ```
- Force enable or disable a specific player:
  ```
  /youtube chat admin enable PokeHowlet
  /youtube chat admin disable PokeHowlet
  ```
- Enforce mandatory announcements:
  In `config.yml`:
  ```yaml
  chat:
    admin:
      streamer-can-disable: false
  ```
  When set to `false`, players running `/youtube chat off` will be notified that announcements are mandatory.

