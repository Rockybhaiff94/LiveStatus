# LiveStatus

> **You only need one Paper plugin JAR.**
>
> There is **NO** separate backend process, **NO** backend JAR, **NO** backend URL, **NO** external API key, and **NO** second plugin. Everything runs directly inside `LiveStatus-1.0.0.jar` on your Minecraft Paper server.

**LiveStatus** is a production-quality, self-contained Minecraft Paper plugin that adds a sleek, non-destructive **LIVE** indicator to an authorized player's existing in-game nametag when, and **ONLY** when:

1. The player has been explicitly enabled by a Minecraft server administrator (`/youtube allow-link <player>` or `/livestatus allow-link <player>`).
2. The player has linked their own YouTube account via Google OAuth 2.0 (`/link youtube`).
3. That linked YouTube account is currently broadcasting live.
4. The active YouTube broadcast is a verified Minecraft gaming stream.

If all four conditions are met:
```
[VIP] PokeHowlet LIVE
```
When the stream ends or switches to another game (e.g. GTA V, Fortnite, or Just Chatting):
```
[VIP] PokeHowlet
```

> [!IMPORTANT]
> The player is already inside Minecraft, so the plugin **never** displays `LIVE Minecraft` or `PokeHowlet LIVE Minecraft`. Only `LIVE` is appended as a clean suffix layer.

---

## Key Highlights

* **Single Self-Contained JAR**: Drop `plugins/LiveStatus-1.0.0.jar` into your Paper server. The Minecraft server acts as its own OAuth callback receiver and API monitor.
* **No Client Mods Required**: Operates 100% server-side on standard Paper servers using the official Paper API.
* **Non-Destructive Suffix Layer**: Preserves all existing nametag prefixes, suffixes, scoreboard teams, and display names set by plugins like LuckPerms, EssentialsX, TAB, or NameTagEdit.
* **Zero Main-Thread Blocking**: All YouTube API queries, Google token refreshes, and disk operations execute asynchronously. Nametag changes are scheduled onto the main server thread safely.
* **Fail-Closed Security**: If YouTube or network connections fail, the LIVE tag is suppressed to prevent stale false-positive indicators.

---

## Project Structure

```
LiveStatus/
├── build.gradle.kts                      # Single Gradle build (Paper API, Java 21 toolchain, JUnit 5)
├── settings.gradle.kts                   # Single project settings
├── gradlew & gradlew.bat                 # Gradle wrapper
├── README.md                             # Main documentation
├── docs/
│   ├── SETUP.md                         # Step-by-step setup guide
│   ├── OAUTH.md                         # Google Cloud & OAuth 2.0 setup guide
│   ├── ADMIN.md                         # Command syntax & permission reference
│   └── COMPATIBILITY.md                 # Nametag preservation & PAPI integration
└── src/
    ├── main/
    │   ├── java/com/example/livestatus/
    │   │   ├── LiveStatusPlugin.java    # Paper plugin lifecycle & DI
    │   │   ├── command/
    │   │   │   ├── YoutubeCommand.java      # Admin commands (/youtube, /livestatus)
    │   │   │   └── LinkYoutubeCommand.java  # Player command (/link youtube)
    │   │   ├── config/
    │   │   │   └── PluginConfig.java        # Configuration with ${ENV} support
    │   │   ├── youtube/
    │   │   │   ├── YouTubeStatus.java       # LiveStatus enum (5 states)
    │   │   │   ├── YouTubeBroadcast.java    # Broadcast record
    │   │   │   ├── YouTubeAccount.java      # Channel metadata record
    │   │   │   ├── YouTubeApiClient.java    # Official YouTube Data API v3 client
    │   │   │   ├── StreamEligibilityDetector.java # Strategy interface
    │   │   │   ├── TopicCategoryDetector.java # Category 20 + Wikipedia Minecraft topic detector
    │   │   │   └── YouTubeService.java      # High-level YouTube query & caching service
    │   │   ├── oauth/
    │   │   │   ├── OAuthStateManager.java   # Cryptographic CSRF state manager (HMAC-SHA256, 5m TTL)
    │   │   │   ├── GoogleOAuthClient.java   # Token exchange & refresh client
    │   │   │   ├── OAuthCallbackServer.java # Embedded HTTP server for OAuth callback
    │   │   │   └── OAuthService.java        # In-game OAuth flow orchestrator
    │   │   ├── storage/
    │   │   │   ├── AccountRecord.java       # Linked account record
    │   │   │   ├── AccountStorage.java      # Storage interface
    │   │   │   └── JsonAccountStorage.java  # Atomic JSON persistence (linked-accounts.json)
    │   │   ├── nametag/
    │   │   │   ├── NametagProvider.java     # Nametag provider abstraction
    │   │   │   ├── PaperTeamNametagProvider.java # Non-destructive Scoreboard team manager
    │   │   │   ├── PlaceholderProvider.java # PAPI-based provider
    │   │   │   ├── CompositeNametagProvider.java # Composite multi-provider
    │   │   │   ├── LiveIndicatorRenderer.java # Indicator rendering abstraction
    │   │   │   └── StaticLiveIndicatorRenderer.java # MiniMessage static renderer
    │   │   ├── player/
    │   │   │   ├── LinkedPlayer.java        # Player data model (UUID primary key)
    │   │   │   ├── PlayerLinkService.java   # Link management & caching
    │   │   │   └── AllowedPlayersStorage.java # Persistent admin authorizations (allowed-players.yml)
    │   │   ├── scheduler/
    │   │   │   └── LiveStatusScheduler.java # 30s async polling & state transition engine
    │   │   └── compatibility/
    │   │       ├── PlaceholderApiHook.java  # Optional PAPI hook
    │   │       └── LiveStatusExpansion.java # %livestatus_*% expansion
    │   └── resources/
    │       ├── plugin.yml                   # Paper plugin descriptor
    │       └── config.yml                   # Default configuration
    └── test/java/com/example/livestatus/
        ├── OAuthStateManagerTest.java       # CSRF protection & token binding tests
        ├── TokenPersistenceTest.java        # Token storage & server restart tests
        ├── MinecraftLiveDetectionTest.java  # Minecraft & non-gaming stream tests
        ├── PluginConfigurationTest.java     # Config parsing & interval tests
        ├── PlayerLinkServiceTest.java       # Admin authorization & unlink tests
        ├── StatusTransitionTest.java        # State transition deduplication tests
        ├── StaticLiveIndicatorRendererTest.java # MiniMessage rendering tests
        ├── ScoreboardSuffixLogicTest.java   # Nametag prefix/suffix preservation tests
        └── PlaceholderApiExpansionTest.java # PAPI expansion tests
```

---

## Installation (Single JAR)

1. **Build the project**:
   ```powershell
   # Windows
   .\gradlew.bat build

   # Linux / macOS
   ./gradlew build
   ```
   *The single compiled JAR is generated at `build/libs/LiveStatus-1.0.0.jar`.*

2. **Copy the JAR into `plugins/`**:
   ```text
   plugins/
   └── LiveStatus-1.0.0.jar
   ```

3. **Start the Paper server**:
   Start Paper to generate the initial configuration files.

4. **Configure Google/YouTube OAuth credentials**:
   Open `plugins/LiveStatus/config.yml` and enter your Google OAuth credentials:
   ```yaml
   youtube:
     client-id: "YOUR_GOOGLE_CLIENT_ID.apps.googleusercontent.com"
     client-secret: "YOUR_GOOGLE_CLIENT_SECRET"
     redirect-uri: "http://localhost:8080/oauth/callback"
     callback-port: 8080
   ```

5. **Restart / reload the plugin**:
   Run `/youtube reload` (or `/livestatus reload`) in the server console or in-game.

6. **Admin authorizes player**:
   ```text
   /youtube allow-link <player>
   ```

7. **Player links their YouTube account**:
   The player executes:
   ```text
   /link youtube
   ```

8. **Player authorizes in browser**:
   The player clicks the `[ CONNECT YOUTUBE ]` link sent in Minecraft chat, signs into Google, and approves read-only YouTube access.

9. **Automatic checking**:
   The plugin automatically checks the channel every 30 seconds asynchronously.

10. **LIVE tag displayed**:
    When the player is live on Minecraft, their nametag automatically displays:
    ```text
    PokeHowlet LIVE
    ```

---

## Configuration (`config.yml`)

```yaml
youtube:
  # Google Cloud OAuth 2.0 Credentials (Web Application)
  client-id: "${YOUTUBE_CLIENT_ID}"
  client-secret: "${YOUTUBE_CLIENT_SECRET}"
  # Redirect URI configured in Google Cloud Console
  redirect-uri: "http://localhost:8080/oauth/callback"
  # Port for the plugin's embedded OAuth callback receiver
  callback-port: 8080
  callback-host: "0.0.0.0"
  request-timeout-ms: 5000

polling:
  # Polling cycle interval in seconds
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

debug:
  enabled: false
```

---

## In-Game Commands & Permissions

### Administrator Commands (`livestatus.admin`)
- `/youtube allow-link <player>` (alias: `/livestatus allow-link`): Authorizes a player to connect their YouTube channel.
- `/youtube revoke-link <player>`: Revokes linking authorization.
- `/youtube status <player>`: Displays connection state, channel title, active broadcast, and stream title.
- `/youtube unlink <player>`: Forcefully unlinks a player's YouTube account and removes their LIVE tag.
- `/youtube reload`: Reloads `config.yml`, `allowed-players.yml`, and storage without restarting the server.
- `/youtube help`: Shows command help.

### Player Commands (`livestatus.link`)
- `/link youtube`: Generates a one-time OAuth link to connect YouTube (requires admin authorization).
- `/link youtube unlink`: Unlinks the player's connected account.

---

## API Capabilities & Official YouTube Limitations

1. **Game Title Selection in YouTube Studio**:
   - YouTube's public Data API v3 does **not** expose a dedicated standalone string property for the internal "Game Title" selected in YouTube Studio Live Control Room.
   - The plugin evaluates `categoryId == "20"` (Gaming) and matches canonical Freebase/Wikipedia Knowledge Graph topic categories (`https://en.wikipedia.org/wiki/Minecraft`).
   - If a creator streams *GTA V*, *Fortnite*, or *Just Chatting*, the plugin identifies this as `LIVE_NOT_MINECRAFT` and suppresses the `LIVE` indicator.
2. **Members-Only Broadcasts**:
   - YouTube Data API v3 does not provide a public `membersOnly` boolean flag. Private and inaccessible broadcasts safely fail closed (`UNKNOWN`), preventing false-positive indicators.
3. **API Quotas**:
   - Default YouTube API quota is 10,000 units/day. The plugin's in-memory caching (30s TTL) and batch query engine prevent quota exhaustion.

---

## Future Extensibility: Animated LIVE Indicators

The visual indicator layer is abstracted behind `LiveIndicatorRenderer`:
- **Current Version**: Uses `StaticLiveIndicatorRenderer` (renders styled text like `<red><bold>LIVE</bold></red>`).
- **Future Animated Version**:
  1. Create `AnimatedLiveIndicatorRenderer` implementing `LiveIndicatorRenderer`.
  2. Maintain frame cycles (e.g. `● LIVE` ➔ `○ LIVE`).
  3. Hook into a scheduled task timer to tick frames without touching YouTube API polling or OAuth code.

---

## Detailed Documentation Links

- [Setup & Server Guide](docs/SETUP.md)
- [Google Cloud & OAuth 2.0 Guide](docs/OAUTH.md)
- [Administrator & Command Reference](docs/ADMIN.md)
- [Nametag Compatibility & Scoreboard Guide](docs/COMPATIBILITY.md)
