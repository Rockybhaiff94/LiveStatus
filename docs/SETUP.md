# LiveStatus - Setup & Server Guide

> **You only need one Paper plugin JAR.**
>
> There is **NO** separate backend process, **NO** backend JAR, **NO** backend URL, **NO** external API key, and **NO** second plugin. Everything runs directly inside `LiveStatus-1.0.0.jar` on your Minecraft Paper server.

---

## 1. Prerequisites

- **Minecraft Server**: Paper 1.21.x (Paper 1.21.4 recommended).
- **Java**: Java 21+ (e.g. Eclipse Adoptium Temurin 21 or OpenJDK 21).
- **Google Cloud Project**: With YouTube Data API v3 enabled and OAuth 2.0 Web Application credentials.

---

## 2. Building the Plugin JAR

Run the build from the project root:

### Windows:
```powershell
.\gradlew.bat build
```

### Linux / macOS:
```bash
./gradlew build
```

The build compiles the plugin, runs the complete automated test suite, and outputs the single self-contained plugin JAR:
```text
build/libs/LiveStatus-1.0.0.jar
```

---

## 3. Installation on Paper Server

1. **Copy the JAR**:
   Copy `build/libs/LiveStatus-1.0.0.jar` into your Paper server's `plugins/` directory:
   ```text
   plugins/
   └── LiveStatus-1.0.0.jar
   ```

2. **Start the Paper server**:
   Start the server to generate `plugins/LiveStatus/config.yml`.

3. **Configure Google OAuth Credentials**:
   Open `plugins/LiveStatus/config.yml` and provide your Google OAuth 2.0 credentials:
   ```yaml
   youtube:
     client-id: "YOUR_GOOGLE_CLIENT_ID.apps.googleusercontent.com"
     client-secret: "YOUR_GOOGLE_CLIENT_SECRET"
     redirect-uri: "http://localhost:8080/oauth/callback" # or https://your-domain.com/oauth/callback
     callback-port: 8080
     callback-host: "0.0.0.0"
   ```
   *(You can also use environment variables such as `${YOUTUBE_CLIENT_ID}` and `${YOUTUBE_CLIENT_SECRET}`).*

4. **Reload the Plugin**:
   Run `/youtube reload` (or `/livestatus reload`) in the server console or in-game (with `livestatus.admin` permission).

---

## 4. End-to-End Workflow

1. **Admin Authorizes Player**:
   ```text
   /youtube allow-link PokeHowlet
   ```
2. **Player Links Account**:
   ```text
   /link youtube
   ```
3. **Player Completes Authorization**:
   - Player clicks the `[ CONNECT YOUTUBE ]` link sent in Minecraft chat.
   - Signs into Google in their web browser and approves read-only YouTube access.
   - The plugin's embedded callback receiver captures the authorization code, exchanges it for tokens, identifies the YouTube channel, and confirms in Minecraft chat.
4. **Automatic 30-Second Checking**:
   - The plugin automatically monitors active broadcasts every 30 seconds asynchronously.
   - When the player starts streaming Minecraft on YouTube:
     ```text
     [VIP] PokeHowlet LIVE
     ```
   - When the stream ends or switches to another game:
     ```text
     [VIP] PokeHowlet
     ```
