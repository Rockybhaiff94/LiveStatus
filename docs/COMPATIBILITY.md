# LiveStatus - Nametag & Scoreboard Compatibility

> **You only need one Paper plugin JAR.**
>
> All nametag rendering, scoreboard management, and PlaceholderAPI expansions run directly within `LiveStatus-1.0.0.jar`.

---

## 1. Core Principles

The plugin adheres strictly to the following principles:

1. **Never blindly replace `Player#setDisplayName`**: Doing so breaks chat formatting plugins, nickname systems, and permission prefixes.
2. **Never blindly replace `Player#setPlayerListName`**: Doing so breaks tablist rank displays and sorting order.
3. **Never destroy or reset scoreboard teams**: Doing so clears rank prefixes (e.g. `[VIP]`, `[MOD]`), colors, and collision rules.
4. **Behave strictly as an additional suffix layer**:
   - Existing nametag: `[VIP] PokeHowlet [MVP]`
   - Live stream active: `[VIP] PokeHowlet [MVP] LIVE`
   - Stream ends: `[VIP] PokeHowlet [MVP]`
5. **No client mods, no floating holograms, no fake armor stands**: The indicator is rendered as part of the vanilla overhead nametag.

---

## 2. Nametag Providers Architecture

The plugin uses an extensible `NametagProvider` abstraction configured via `nametag.provider` in `config.yml`:

```
              NametagProvider (Interface)
                      |
         +------------+------------+
         |                         |
 PaperTeamNametagProvider    PlaceholderProvider
         |                         |
         +------------+------------+
                      |
           CompositeNametagProvider
```

### Provider 1: `team` (Default: `PaperTeamNametagProvider`)
Designed for standard Paper servers:
* **Single-Member Team**: If the player is the sole member of an existing team, the plugin appends ` LIVE` to the team suffix and saves the original suffix. When the stream concludes, the original suffix is restored.
* **Shared Team**: If multiple players share a team (e.g., an `admin` team), modifying the team directly would cause all admins to display `LIVE`. To prevent this, the plugin clones the team's visual properties (prefix, color, collision rules, nametag visibility) to an isolated dynamic team (`ytl_<shortUuid>`), applies ` LIVE` to that player only, and restores their original team membership when streaming ends.
* **No Existing Team**: If the player has no team, a dynamic team is registered with prefix `""` and suffix ` LIVE`. On stream conclusion, the dynamic team is cleanly unregistered.

### Provider 2: `placeholder` (`PlaceholderProvider`)
Designed for servers utilizing dedicated nametag/tablist managers (such as **TAB** by NEZNAMY or **NameTagEdit**):
* When `nametag.provider: placeholder` is selected, LiveStatus does not touch scoreboard teams directly.
* Instead, it exposes `%livestatus_suffix%` and `%livestatus_live%` for your nametag plugin to format.

### Provider 3: `composite` (`CompositeNametagProvider`)
Applies native scoreboard team suffixes and simultaneously updates PlaceholderAPI cache.

---

## 3. PlaceholderAPI Reference

When PlaceholderAPI is installed, the following placeholders are available:

| Placeholder | Example Output (Live) | Example Output (Offline) | Description |
|---|---|---|---|
| `%livestatus_live%` | `true` | `false` | Boolean indicating if the player is currently live streaming Minecraft. |
| `%livestatus_status%` | `LIVE_MINECRAFT` | `OFFLINE` | Raw status enum string. |
| `%livestatus_status_display%` | `Live Minecraft` | `Offline` | Formatted user-friendly status name. |
| `%livestatus_game%` | `Minecraft` | `""` (empty) | Name of the verified game (only present when live Minecraft). |
| `%livestatus_suffix%` | ` LIVE` | `""` (empty) | Formatted suffix ready to append to any nametag or tablist config. |
| `%livestatus_channel%` | `PokeHowlet Official` | `""` | Linked YouTube channel title. |
| `%livestatus_channel_id%` | `UC1234567890abcdef` | `""` | Linked YouTube channel ID. |

---

## 4. Example Integration with TAB plugin

If you use [TAB](https://github.com/NEZNAMY/TAB), you can easily add the LIVE indicator to overhead nametags:

In `TAB/config.yml`:
```yaml
scoreboard-teams:
  enabled: true
  # Add %livestatus_suffix% after player's existing suffix
  suffix: "%vault_suffix%%livestatus_suffix%"
```
This ensures zero team conflicts and allows TAB's animation and priority sorting to work alongside LiveStatus!
