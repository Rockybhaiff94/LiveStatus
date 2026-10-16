# MCast (LiveStatus)

[![PaperMC](https://img.shields.io/badge/Paper-1.21.x-blue.svg)](https://papermc.io)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://www.oracle.com/java/)
[![Next.js](https://img.shields.io/badge/Next.js-16-black.svg)](https://nextjs.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Web Portal](https://img.shields.io/badge/Portal-mcast.fun-FF7A00.svg)](https://mcast.fun)

> **MCast: Full-stack Minecraft YouTube live-stream platform with centralized OAuth, in-game LIVE nametags, automated chat broadcasts, and web portal.**

**MCast** connects Minecraft servers with YouTube Live broadcasting. When an authorized player goes live streaming Minecraft on YouTube, MCast seamlessly adds an elegant, non-destructive **LIVE** badge next to their in-game nametag, delivers fair anti-spam chat announcements, and provides players with a web dashboard at **[mcast.fun](https://mcast.fun)**.

With the centralized MCast architecture, **server administrators do not need Google Cloud accounts, API keys, or complex port forwarding**. The plugin works right out of the box.

```
[VIP] PokeHowlet LIVE
```
*When the stream ends or switches to another game (e.g. GTA V or Fortnite), the badge disappears automatically:*
```
[VIP] PokeHowlet
```

---

## Architecture Overview

MCast is a complete monorepo ecosystem designed for performance, security, and scalability:

```
┌────────────────────────────────────────────────────────────────────────┐
│                              MCast Monorepo                            │
├───────────────────────┬────────────────────────┬───────────────────────┤
│ 🎮 Minecraft Plugin   │ ⚡ Central Backend API  │ 🌐 Web Portal         │
│ (src/)                │ (backend/)             │ (website/)            │
│                       │                        │                       │
│ • Paper 1.21+ API     │ • Express + TypeScript │ • Next.js 16 (React 19)│
│ • Non-destructive team│ • PostgreSQL (Supabase)│ • Tailwind CSS v4     │
│   nametag suffix      │ • AES-256-GCM token    │ • Google OAuth flow   │
│ • Adventure chat      │   encryption           │ • Streamer dashboard  │
│   announcements       │ • Batch YouTube polling│ • Deployed on Vercel  │
│ • Out-of-the-box ready│ • Deployed on Render   │   (mcast.fun)         │
└───────────────────────┴────────────────────────┴───────────────────────┘
```

1. **Minecraft Paper Plugin (`src/`)**: Drop-in Paper 1.21+ JAR. Maintains scoreboard prefixes/suffixes non-destructively, handles player commands (`/youtube`, `/link youtube`), and schedules async polling without blocking the main server tick.
2. **Central Backend API (`backend/`)**: Node.js/TypeScript microservice that securely handles Google OAuth 2.0 token exchanges, cryptographic CSRF validation, server identity verification, and efficient batch live-status checks against YouTube Data API v3.
3. **Web Portal & Dashboard (`website/`)**: Modern web application hosted at [mcast.fun](https://mcast.fun) where streamers link their YouTube accounts, check their active stream statuses, and inspect channel diagnostics.

---

## Key Highlights

* **Zero Cloud Setup for Server Owners**: Centralized OAuth handles Google token exchanges and refreshes securely through `mcast.fun`. Server owners never need to create a Google Cloud Project or manage OAuth credentials.
* **Non-Destructive Nametag Suffix**: Integrates cleanly with existing permissions and nametag plugins (LuckPerms, EssentialsX, TAB, NameTagEdit) without overwriting player prefixes, suffixes, or scoreboard teams.
* **Automated Fair Chat Broadcasts**: Centralized Adventure MiniMessage announcements with independent streamer cooldowns, random interval rotation, anti-spam safeguards, and full administrator overrides.
* **Accurate Game Detection**: Verifies active live broadcasts against YouTube's Gaming category (`categoryId: 20`) and Minecraft canonical topic IDs (`/m/09s1f`). Prevents false-positives when streamers play other games or chat.
* **Fail-Closed Security**: If YouTube API is unreachable or network connectivity drops, badges and broadcasts safely fail closed to eliminate stale indicator bugs.
* **Cross-Server Auto-Linking**: Streamers who link their YouTube account once are recognized across all servers connected to MCast.

---

## Monorepo Structure

```
MCast/
├── src/                                  # Minecraft Paper Plugin (Java 21)
│   ├── main/java/com/example/livestatus/
│   │   ├── LiveStatusPlugin.java         # Plugin lifecycle & DI container
│   │   ├── mcast/                        # Centralized MCast API client & registration
│   │   ├── nametag/                      # Scoreboard team & PAPI nametag providers
│   │   ├── chat/                         # Fair rotation chat scheduler & formatters
│   │   ├── youtube/                      # Stream detection & topic categorization
│   │   ├── command/                      # In-game admin & player commands
│   │   └── storage/                      # Persistent account & streamer settings
│   └── main/resources/
│       ├── plugin.yml                    # Paper plugin descriptor
│       └── config.yml                    # Default plugin configuration
├── backend/                              # Centralized Backend API (Node.js/TS)
│   ├── src/
│   │   ├── db/                           # Supabase / PostgreSQL schema & client
│   │   ├── routes/                       # Express routes (OAuth, servers, streamers)
│   │   ├── services/                     # YouTube live polling & token manager
│   │   └── crypto/                       # AES-256-GCM encryption & HMAC CSRF
│   ├── package.json
│   └── Dockerfile
├── website/                              # Web Portal (Next.js 16 + Tailwind CSS v4)
│   ├── src/
│   │   ├── app/                          # Next.js App Router (dashboard, auth, docs)
│   │   └── components/                   # Live nametag preview & UI components
│   └── package.json
├── docs/                                 # Detailed documentation guides
│   ├── SETUP.md                          # Server setup & configuration guide
│   ├── ADMIN.md                          # Full command syntax & permissions
│   ├── OAUTH.md                          # OAuth flow & security architecture
│   └── COMPATIBILITY.md                  # Scoreboard, TAB & PAPI guide
├── build.gradle.kts                      # Gradle build configuration
├── render.yaml                           # Render cloud deployment blueprint
└── README.md                             # Project overview
```

---

## Quick Start (Minecraft Server)

### 1. Build or Download the Plugin

Build the Paper plugin JAR using the Gradle wrapper:

```powershell
# Windows
.\gradlew.bat build

# Linux / macOS
./gradlew build
```

The compiled plugin JAR is located at:
```text
build/libs/LiveStatus-1.0.0.jar
```

### 2. Install on Paper Server

1. Drop `LiveStatus-1.0.0.jar` into your Minecraft server's `plugins/` directory.
2. Start the server. The plugin automatically generates `plugins/LiveStatus/config.yml` pre-configured for the centralized MCast service.

### 3. Link YouTube Account

1. **Admin authorizes player**:
   ```text
   /youtube allow-link <player>
   ```
2. **Player requests linking link in-game**:
   ```text
   /link youtube
   ```
3. **Player completes authorization**:
   The player clicks the `[ CONNECT YOUTUBE ]` link sent in Minecraft chat, signs in via [mcast.fun](https://mcast.fun), and authorizes read-only YouTube access.
4. **Automatic LIVE badge**:
   Whenever the player streams Minecraft live on YouTube, their in-game nametag automatically updates to display `LIVE`, and stream announcements are scheduled in chat.

---

## Plugin Configuration (`config.yml`)

```yaml
# ==============================================================================
# MCast LiveStatus - Centralized Minecraft Paper Plugin Configuration
# Ready out of the box! Centralized OAuth is managed via https://mcast.fun
# ==============================================================================

mcast:
  # Centralized MCast API endpoint
  api-url: "https://api.mcast.fun"
  # Web linking portal URL displayed to players
  web-url: "https://mcast.fun"
  request-timeout-ms: 5000

linking:
  # When true, administrators must approve players with /youtube allow-link
  require-admin-approval: true

polling:
  # YouTube status check interval in seconds (runs 100% asynchronously)
  interval-seconds: 30

nametag:
  enabled: true
  provider: "team" # Options: "team", "placeholder", or "composite"
  live-text: "LIVE"
  live-format: "<red><bold>LIVE</bold></red>"
  separator: " "

detection:
  require-gaming-category: true
  require-minecraft: true
  allow-title-fallback: false

security:
  fail-closed: true # Safely removes LIVE tag if API fails

compatibility:
  placeholderapi: true # Registers %livestatus_*% expansion
```

---

## Commands & Permissions

### Administrator Commands (`livestatus.admin` or `ytlive.admin`)
* `/youtube allow-link <player>`: Authorizes a player to connect their YouTube channel.
* `/youtube revoke-link <player>`: Revokes linking authorization.
* `/youtube status <player>`: Displays connection state, channel title, active broadcast, and stream title.
* `/youtube unlink <player>`: Forcefully unlinks a player's YouTube account and removes their LIVE tag.
* `/youtube reload`: Reloads plugin configuration and local storage.
* `/youtube help`: Displays command syntax help.

#### Announcement Admin Controls (`youtubelivetag.chat.admin`):
* `/youtube chat admin status`: Displays global scheduler diagnostics and active streamers.
* `/youtube chat admin on|off`: Globally toggles live stream chat announcements server-wide.
* `/youtube chat admin enable|disable <player>`: Manages announcements for an individual streamer.
* `/youtube chat admin interval <player> <minutes|random>`: Overrides announcement cooldown.
* `/youtube chat admin title <player> <text>`: Overrides streamer's broadcast announcement text.

### Streamer Commands (`livestatus.chat` or `youtubelivetag.chat`)
* `/youtube chat` / `/youtube chat status`: Displays your announcement status, cooldown, and title.
* `/youtube chat on|off`: Toggles your own broadcast announcements.
* `/youtube chat title <text>`: Sets a custom announcement message.
* `/youtube chat title clear`: Restores the dynamic YouTube stream title.
* `/youtube chat interval <minutes|random|default>`: Customizes announcement cooldown timing.

### Player Commands (`livestatus.link`)
* `/link youtube`: Generates a secure one-time link to connect your YouTube channel.
* `/link youtube unlink`: Disconnects your connected YouTube channel.

---

## Development & Deployment

### Backend Service (`backend/`)
```bash
cd backend
npm install
npm run dev     # Starts development server with tsx
npm run build   # Compiles TypeScript to dist/
npm run test    # Runs Vitest unit & integration tests
```
*Automated deployment configuration is available in `render.yaml` for Render.*

### Web Portal (`website/`)
```bash
cd website
npm install
npm run dev     # Runs Next.js local development server (port 3000)
npm run build   # Builds production Next.js bundle with Turbopack
```
*Deploy to Vercel by linking the `Rockybhaiff94/MCast` repository with root directory set to `website`.*

---

## Documentation

* [Server Installation & Quick Start](docs/SETUP.md)
* [Commands & Permissions Reference](docs/ADMIN.md)
* [OAuth & Security Architecture](docs/OAUTH.md)
* [Nametag & Scoreboard Compatibility](docs/COMPATIBILITY.md)

---

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.
