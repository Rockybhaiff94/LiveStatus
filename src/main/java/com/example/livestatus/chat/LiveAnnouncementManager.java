package com.example.livestatus.chat;

import com.example.livestatus.config.PluginConfig;
import com.example.livestatus.player.AllowedPlayersStorage;
import com.example.livestatus.stats.PluginStatistics;
import com.example.livestatus.storage.AccountRecord;
import com.example.livestatus.storage.AccountStorage;
import com.example.livestatus.storage.StreamerConfigStore;
import com.example.livestatus.youtube.YouTubeBroadcast;
import com.example.livestatus.youtube.YouTubeService;
import com.example.livestatus.youtube.YouTubeStatus;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * Centralized manager coordinating live announcement eligibility, fair randomized rotation,
 * cooldown calculation, and Adventure chat message dispatch.
 */
public class LiveAnnouncementManager {

    private final Plugin plugin;
    private final PluginConfig config;
    private final YouTubeService youTubeService;
    private final AllowedPlayersStorage allowedStorage;
    private final AccountStorage accountStorage;
    private final StreamerConfigStore configStore;
    private final AnnouncementFormatter formatter;
    private final PluginStatistics statistics;
    private final Logger logger;

    private final Map<UUID, AnnouncementState> announcementStates = new ConcurrentHashMap<>();
    private final Map<UUID, String> previousKnownVideoIds = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> previousLiveStates = new ConcurrentHashMap<>();

    private volatile Instant lastGlobalAnnouncementTime = Instant.EPOCH;

    public LiveAnnouncementManager(
            Plugin plugin,
            PluginConfig config,
            YouTubeService youTubeService,
            AllowedPlayersStorage allowedStorage,
            AccountStorage accountStorage,
            StreamerConfigStore configStore,
            AnnouncementFormatter formatter,
            PluginStatistics statistics,
            Logger logger
    ) {
        this.plugin = plugin;
        this.config = config;
        this.youTubeService = youTubeService;
        this.allowedStorage = allowedStorage;
        this.accountStorage = accountStorage;
        this.configStore = configStore;
        this.formatter = formatter;
        this.statistics = statistics;
        this.logger = logger;
    }

    /**
     * Periodically evaluated by the centralized AnnouncementScheduler.
     */
    public synchronized void evaluateAndAnnounceNext() {
        if (!config.isChatEnabled() || !config.isAdminChatEnabled()) {
            return;
        }

        Instant now = Instant.now();

        // Check global minimum gap between ANY announcements
        Duration elapsedSinceLastGlobal = Duration.between(lastGlobalAnnouncementTime, now);
        if (elapsedSinceLastGlobal.getSeconds() < config.getGlobalMinimumGapSeconds()) {
            return;
        }

        List<LiveStreamState> eligible = getEligibleStreamers(now);
        if (eligible.isEmpty()) {
            return;
        }

        LiveStreamState selected = selectNextStreamer(eligible, now);
        if (selected == null) {
            return;
        }

        executeAnnouncement(selected, now);
    }

    /**
     * Gathers all candidate streamers, refreshes stream transitions, and filters for eligibility.
     */
    public List<LiveStreamState> getEligibleStreamers(Instant now) {
        Set<UUID> candidateUuids = getCandidateUuids();
        List<LiveStreamState> eligibleList = new ArrayList<>();

        for (UUID uuid : candidateUuids) {
            LiveStreamState state = buildCurrentState(uuid);
            if (state == null) {
                continue;
            }

            // Track stream life cycle transitions for clean logging
            updateStreamTransitions(state);

            if (state.isEligibleForAnnouncement(now, config.isAdminChatEnabled() && config.isChatEnabled())) {
                eligibleList.add(state);
            }
        }

        return eligibleList;
    }

    /**
     * Builds candidate UUID set: authorized players who have linked YouTube accounts.
     */
    public Set<UUID> getCandidateUuids() {
        Set<UUID> candidates = new HashSet<>();
        for (UUID uuid : allowedStorage.getAllowedUuids()) {
            if (accountStorage.findByUuid(uuid).isPresent()) {
                candidates.add(uuid);
            }
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID uuid = p.getUniqueId();
            if (allowedStorage.isAllowed(uuid) && accountStorage.findByUuid(uuid).isPresent()) {
                candidates.add(uuid);
            }
        }
        return candidates;
    }

    /**
     * Builds the aggregated LiveStreamState for a player.
     */
    public LiveStreamState buildCurrentState(UUID uuid) {
        boolean authorized = allowedStorage.isAllowed(uuid);
        Optional<AccountRecord> optAccount = accountStorage.findByUuid(uuid);
        boolean linked = optAccount.isPresent();

        String playerName = "Unknown";
        Player onlinePlayer = Bukkit.getPlayer(uuid);
        if (onlinePlayer != null) {
            playerName = onlinePlayer.getName();
        } else if (optAccount.isPresent() && optAccount.get().playerName() != null) {
            playerName = optAccount.get().playerName();
        } else {
            String storedName = allowedStorage.getAllowedPlayersMap().get(uuid);
            if (storedName != null) {
                playerName = storedName;
            }
        }

        String channelId = optAccount.map(AccountRecord::youtubeChannelId).orElse(null);

        // Fetch cached YouTube broadcast status
        Optional<YouTubeService.CachedBroadcastStatus> optCached = youTubeService.getCachedStatus(uuid);
        boolean currentlyLive = false;
        boolean isMinecraft = false;
        String videoId = null;
        String streamTitle = null;

        if (optCached.isPresent()) {
            YouTubeService.CachedBroadcastStatus cached = optCached.get();
            currentlyLive = cached.status() == YouTubeStatus.LIVE_MINECRAFT;
            if (cached.broadcast() != null) {
                YouTubeBroadcast broadcast = cached.broadcast();
                videoId = broadcast.broadcastId();
                streamTitle = broadcast.title();
                isMinecraft = broadcast.isMinecraft();
            }
        }

        // Fetch streamer settings
        StreamerChatSettings settings = configStore.getOrCreate(uuid, playerName);
        AnnouncementState announcementState = announcementStates.computeIfAbsent(uuid, AnnouncementState::new);

        // Check if offline streamer announcement is disabled
        if (!config.isAnnounceOfflineStreamers() && (onlinePlayer == null || !onlinePlayer.isOnline())) {
            currentlyLive = false;
        }

        String streamUrl = videoId != null ? "https://www.youtube.com/watch?v=" + videoId : null;

        return new LiveStreamState(
                uuid,
                playerName,
                channelId,
                authorized,
                linked,
                currentlyLive,
                videoId,
                streamTitle,
                streamUrl,
                isMinecraft,
                settings.isEffectivelyEnabled(),
                settings.getIntervalMode(),
                settings.getCustomIntervalMinutes(),
                settings.getCustomTitle(),
                announcementState.getLastAnnouncedTime(),
                announcementState.getNextEligibleTime()
        );
    }

    /**
     * Monitors stream lifecycle: logs new stream starts, resets cooldowns on new video IDs, and logs offline events.
     */
    private void updateStreamTransitions(LiveStreamState state) {
        UUID uuid = state.minecraftUuid();
        String currentVideoId = state.videoId();
        boolean isCurrentlyLive = state.currentlyLive();

        Boolean wasLive = previousLiveStates.put(uuid, isCurrentlyLive);
        String previousVideoId = previousKnownVideoIds.get(uuid);

        if (isCurrentlyLive && currentVideoId != null) {
            if (previousVideoId == null || !previousVideoId.equals(currentVideoId)) {
                // Brand new stream detected!
                previousKnownVideoIds.put(uuid, currentVideoId);
                AnnouncementState aState = announcementStates.computeIfAbsent(uuid, AnnouncementState::new);
                aState.resetForNewStream(currentVideoId);
                logger.info("[YouTubeLiveTag] Streamer " + state.playerName() + " started live stream " + currentVideoId + ".");
                if (historyTracker != null) {
                    historyTracker.onStreamStart(uuid, state.playerName(), state.youtubeChannelId(), state.playerName(), currentVideoId, state.streamTitle());
                }
            }
        } else if (Boolean.TRUE.equals(wasLive) && !isCurrentlyLive) {
            // Stream ended
            previousKnownVideoIds.remove(uuid);
            logger.info("[YouTubeLiveTag] Streamer " + state.playerName() + " went offline.");
            if (historyTracker != null) {
                historyTracker.onStreamEnd(uuid);
            }
        }
    }

    private com.example.livestatus.history.LiveHistoryTracker historyTracker;

    public void setHistoryTracker(com.example.livestatus.history.LiveHistoryTracker historyTracker) {
        this.historyTracker = historyTracker;
    }

    /**
     * Fair randomized selection:
     * Calculates wait time for each eligible streamer.
     * Weights longer-waiting streamers higher, adds randomness, and selects one.
     */
    public LiveStreamState selectNextStreamer(List<LiveStreamState> eligible, Instant now) {
        if (eligible == null || eligible.isEmpty()) {
            return null;
        }
        if (eligible.size() == 1) {
            return eligible.get(0);
        }

        long totalWeight = 0;
        long[] weights = new long[eligible.size()];

        for (int i = 0; i < eligible.size(); i++) {
            LiveStreamState s = eligible.get(i);
            Instant eligibleAt = s.nextEligibleTime() != null ? s.nextEligibleTime() : now;
            long waitSeconds = Math.max(0L, Duration.between(eligibleAt, now).toSeconds());
            // Add base weight of 60 seconds so freshly eligible streamers still have a non-zero fair probability
            long weight = waitSeconds + 60L;
            weights[i] = weight;
            totalWeight += weight;
        }

        if (totalWeight <= 0) {
            return eligible.get(ThreadLocalRandom.current().nextInt(eligible.size()));
        }

        long randomValue = ThreadLocalRandom.current().nextLong(totalWeight);
        long currentSum = 0;
        for (int i = 0; i < eligible.size(); i++) {
            currentSum += weights[i];
            if (randomValue < currentSum) {
                return eligible.get(i);
            }
        }

        return eligible.get(eligible.size() - 1);
    }

    /**
     * Calculates the re-announcement cooldown duration based on streamer's interval mode.
     */
    public Duration calculateCooldown(LiveStreamState state) {
        int minutes;
        IntervalMode mode = state.intervalMode();

        switch (mode) {
            case CUSTOM -> {
                int configured = state.customIntervalMinutes();
                minutes = Math.max(config.getMinimumIntervalMinutes(), Math.min(config.getMaximumIntervalMinutes(), configured));
            }
            case RANDOM -> {
                int minR = Math.max(1, config.getRandomMinimumMinutes());
                int maxR = Math.max(minR, config.getRandomMaximumMinutes());
                minutes = ThreadLocalRandom.current().nextInt(minR, maxR + 1);
            }
            default -> minutes = config.getDefaultIntervalMinutes();
        }

        return Duration.ofMinutes(minutes);
    }

    /**
     * Executes the announcement on the main Minecraft server thread.
     */
    private void executeAnnouncement(LiveStreamState state, Instant now) {
        Duration cooldown = calculateCooldown(state);
        AnnouncementState aState = announcementStates.computeIfAbsent(state.minecraftUuid(), AnnouncementState::new);
        aState.recordAnnouncement(now, cooldown, state.videoId());
        this.lastGlobalAnnouncementTime = now;
        statistics.recordAnnouncement(state.playerName());

        List<Component> formatted = formatter.format(state);

        // Safe broadcast on the main server thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.hasPermission("youtubelivetag.chat.bypass") || player.hasPermission("livestatus.chat.bypass")) {
                    continue;
                }
                for (Component line : formatted) {
                    player.sendMessage(line);
                }
            }
            logger.info("[YouTubeLiveTag] Announcement sent for " + state.playerName() + ".");
        });
    }

    public Instant getLastGlobalAnnouncementTime() {
        return lastGlobalAnnouncementTime;
    }

    public StreamerConfigStore getConfigStore() {
        return configStore;
    }

    public PluginStatistics getStatistics() {
        return statistics;
    }

    public AnnouncementState getAnnouncementState(UUID uuid) {
        return announcementStates.computeIfAbsent(uuid, AnnouncementState::new);
    }

    /**
     * Returns estimated duration until the next announcement can be sent.
     */
    public Duration getEstimatedTimeToNextAnnouncement() {
        Instant now = Instant.now();
        Instant globalReadyAt = lastGlobalAnnouncementTime.plusSeconds(config.getGlobalMinimumGapSeconds());
        Instant earliestEligible = null;

        for (UUID uuid : getCandidateUuids()) {
            LiveStreamState state = buildCurrentState(uuid);
            if (state != null && state.currentlyLive() && state.chatEnabled()) {
                Instant streamerReady = state.nextEligibleTime() != null ? state.nextEligibleTime() : now;
                Instant effectiveReady = streamerReady.isAfter(globalReadyAt) ? streamerReady : globalReadyAt;
                if (earliestEligible == null || effectiveReady.isBefore(earliestEligible)) {
                    earliestEligible = effectiveReady;
                }
            }
        }

        if (earliestEligible == null) {
            return Duration.between(now, globalReadyAt.isAfter(now) ? globalReadyAt : now);
        }

        if (earliestEligible.isBefore(now)) {
            return Duration.ZERO;
        }

        return Duration.between(now, earliestEligible);
    }
}
