import { db, StreamCacheRecord } from '../db/index.js';
import { decryptToken } from '../crypto/encryption.js';

export const GAMING_CATEGORY_ID = '20';
export const MINECRAFT_TOPIC_WIKI = 'https://en.wikipedia.org/wiki/Minecraft';
export const MINECRAFT_FREEBASE_MID = '/m/09v6kpg';

/**
 * Parses game/category title directly from YouTube watch page HTML/ytInitialData.
 * Zero quota cost, supports creator studio game selection even when topicCategories is unassigned or lagged.
 */
export function extractGameTitleFromHtml(html: string): string | null {
  if (!html) return null;

  // 1. Box Art richMetadataRenderer (Standard for gaming streams)
  const boxArtMatch1 = html.match(/"richMetadataRenderer":\s*\{[^}]*?"title":\s*\{\s*"simpleText":\s*"([^"]+)"\s*\}[^}]*?"style":\s*"RICH_METADATA_RENDERER_STYLE_BOX_ART"/);
  if (boxArtMatch1) return boxArtMatch1[1];

  const boxArtMatch2 = html.match(/"style":\s*"RICH_METADATA_RENDERER_STYLE_BOX_ART"[^}]*?"title":\s*\{\s*"simpleText":\s*"([^"]+)"\s*\}/);
  if (boxArtMatch2) return boxArtMatch2[1];

  // 2. Browse game call-to-action
  const browseGameMatch1 = html.match(/"title":\s*\{\s*"simpleText":\s*"([^"]+)"\s*\}[^}]*?"callToAction":\s*\{\s*"simpleText":\s*"Browse game"/);
  if (browseGameMatch1) return browseGameMatch1[1];

  const browseGameMatch2 = html.match(/"callToAction":\s*\{\s*"simpleText":\s*"Browse game"[^}]*?"title":\s*\{\s*"simpleText":\s*"([^"]+)"\s*\}/);
  if (browseGameMatch2) return browseGameMatch2[1];

  // 3. metadataRowRenderer Game row
  const gameRowMatch = html.match(/"title":\s*\{\s*"runs":\s*\[\s*\{\s*"text":\s*"Game"\s*\}\s*\]\s*\},\s*"contents":\s*\[\s*\{\s*"runs":\s*\[\s*\{\s*"text":\s*"([^"]+)"/);
  if (gameRowMatch) return gameRowMatch[1];

  // 4. Compact metadata gameTitle
  const gameTitleMatch = html.match(/"gameTitle":\s*\{\s*"simpleText":\s*"([^"]+)"\s*\}/);
  if (gameTitleMatch) return gameTitleMatch[1];

  // 5. HTML meta tags
  const metaMatch = html.match(/<meta\s+itemprop="game"\s+content="([^"]+)"/i);
  if (metaMatch) return metaMatch[1];

  return null;
}

export interface ChannelProfile {
  channelId: string;
  title: string;
  customUrl: string | null;
  avatarUrl: string | null;
}

export interface LiveBroadcastResult {
  status: 'LIVE_MINECRAFT' | 'LIVE_NOT_MINECRAFT' | 'OFFLINE' | 'UNKNOWN';
  isLive: boolean;
  isMinecraft: boolean;
  videoId?: string;
  title?: string;
  streamUrl?: string;
  checkedAt: Date;
  expiresAt: Date;
  error?: string;
}

export function sanitizeApiError(rawBody: string, status: number): string {
  if (!rawBody || rawBody.trim().length === 0) {
    return `HTTP ${status}`;
  }

  // Remove any OAuth tokens or secrets that might inadvertently appear in error payloads
  const sanitized = rawBody
    .replace(/(?:Bearer\s+)[a-zA-Z0-9_\-\.]+/gi, 'Bearer [REDACTED]')
    .replace(/(?:ya29\.[a-zA-Z0-9_\-]+)/g, '[REDACTED_ACCESS_TOKEN]')
    .replace(/(?:client_secret=)[^&"'\s]+/gi, 'client_secret=[REDACTED]')
    .replace(/(?:refresh_token=)[^&"'\s]+/gi, 'refresh_token=[REDACTED]')
    .replace(/(?:code=)[^&"'\s]+/gi, 'code=[REDACTED]');

  try {
    const json = JSON.parse(sanitized);
    if (json.error) {
      const code = json.error.code || status;
      const message = json.error.message || 'Unknown API error';
      const details = Array.isArray(json.error.errors) && json.error.errors.length > 0
        ? json.error.errors.map((e: any) => `[${e.reason || e.domain || 'error'}]: ${e.message}`).join(', ')
        : '';
      return details ? `HTTP ${code} (${details})` : `HTTP ${code}: ${message}`;
    }
  } catch {
    // Non-JSON response
  }

  const singleLine = sanitized.replace(/\s+/g, ' ').trim();
  const truncated = singleLine.length > 250 ? singleLine.slice(0, 250) + '...' : singleLine;
  return `HTTP ${status}: ${truncated}`;
}

export class YouTubeService {
  private clientId: string;
  private clientSecret: string;
  private redirectUri: string;
  private apiKey: string;
  private tokenCache = new Map<string, { accessToken: string; expiresAt: number }>();
  private lastLoggedErrors = new Map<string, { message: string; timestamp: number }>();

  constructor() {
    this.clientId = process.env.GOOGLE_CLIENT_ID || '';
    this.clientSecret = process.env.GOOGLE_CLIENT_SECRET || '';
    this.redirectUri = process.env.GOOGLE_REDIRECT_URI || 'https://api.mcast.fun/v1/oauth/callback';
    this.apiKey = process.env.YOUTUBE_API_KEY || process.env.GOOGLE_API_KEY || '';
  }

  /**
   * Throttles error logging to avoid repeatedly printing the same error every 30 seconds.
   */
  public logErrorThrottled(channelId: string, message: string, intervalMs = 300_000): void {
    const now = Date.now();
    const prev = this.lastLoggedErrors.get(channelId);
    if (!prev || prev.message !== message || (now - prev.timestamp) > intervalMs) {
      console.warn(`[YouTube] Channel ${channelId}: ${message}`);
      this.lastLoggedErrors.set(channelId, { message, timestamp: now });
    }
  }

  /**
   * Captures and sanitizes error response body from YouTube API calls without exposing tokens.
   */
  public async captureSanitizedError(res: Response, endpoint: string): Promise<string> {
    try {
      const text = await res.text();
      const sanitized = sanitizeApiError(text, res.status);
      return `${endpoint} failed with ${sanitized}`;
    } catch {
      return `${endpoint} failed with HTTP ${res.status}`;
    }
  }

  /**
   * Exchanges an OAuth authorization code for Google access and refresh tokens.
   */
  public async exchangeCode(code: string): Promise<{ accessToken: string; refreshToken: string; expiresIn: number }> {
    const params = new URLSearchParams({
      code,
      client_id: this.clientId,
      client_secret: this.clientSecret,
      redirect_uri: this.redirectUri,
      grant_type: 'authorization_code',
    });

    const res = await fetch('https://oauth2.googleapis.com/token', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: params.toString(),
    });

    if (!res.ok) {
      const errText = await res.text();
      const sanitized = sanitizeApiError(errText, res.status);
      throw new Error(`Google token exchange failed: ${sanitized}`);
    }

    const data = (await res.json()) as any;
    if (!data.access_token) {
      throw new Error('Google token response did not contain an access_token');
    }

    return {
      accessToken: data.access_token,
      refreshToken: data.refresh_token || '',
      expiresIn: data.expires_in || 3600,
    };
  }

  /**
   * Refreshes an expired access token using the stored refresh token.
   */
  public async refreshAccessToken(refreshToken: string): Promise<{ accessToken: string; expiresIn: number }> {
    if (!refreshToken || refreshToken.trim().length === 0) {
      throw new Error('OAuth token expired with no refresh token. Reauthorization required: run /link youtube in Minecraft to reconnect.');
    }

    const params = new URLSearchParams({
      client_id: this.clientId,
      client_secret: this.clientSecret,
      refresh_token: refreshToken,
      grant_type: 'refresh_token',
    });

    const res = await fetch('https://oauth2.googleapis.com/token', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: params.toString(),
    });

    if (!res.ok) {
      const errText = await res.text();
      const sanitized = sanitizeApiError(errText, res.status);
      if (errText.includes('invalid_grant') || errText.includes('unauthorized_client') || res.status === 400 || res.status === 401) {
        throw new Error(`OAuth refresh token revoked or expired (${sanitized}). Reauthorization required: run /link youtube in Minecraft to reconnect.`);
      }
      throw new Error(`Google token refresh failed: ${sanitized}`);
    }

    const data = (await res.json()) as any;
    if (!data.access_token) {
      throw new Error('Google token refresh response did not contain an access_token');
    }
    return {
      accessToken: data.access_token,
      expiresIn: data.expires_in || 3600,
    };
  }

  /**
   * Obtains an unexpired access token for a channel, using cached token if still valid.
   */
  public async getAccessTokenForChannel(channelId: string, refreshToken: string): Promise<string> {
    const cached = this.tokenCache.get(channelId);
    if (cached && cached.expiresAt > Date.now() + 60_000) {
      return cached.accessToken;
    }

    const tokenResult = await this.refreshAccessToken(refreshToken);
    this.tokenCache.set(channelId, {
      accessToken: tokenResult.accessToken,
      expiresAt: Date.now() + (tokenResult.expiresIn || 3600) * 1000,
    });
    return tokenResult.accessToken;
  }

  /**
   * Clears cached access token for a channel (e.g. after HTTP 401).
   */
  public clearAccessTokenCache(channelId: string): void {
    this.tokenCache.delete(channelId);
  }

  /**
   * Fetches the YouTube channel profile of the authenticated user.
   * Quota cost: 1 unit (channels.list?part=snippet&mine=true).
   */
  public async getChannelProfile(accessToken: string): Promise<ChannelProfile> {
    const url = 'https://www.googleapis.com/youtube/v3/channels?part=snippet&mine=true';
    const res = await fetch(url, {
      headers: {
        Authorization: `Bearer ${accessToken}`,
        Accept: 'application/json',
      },
    });

    if (!res.ok) {
      const err = await this.captureSanitizedError(res, 'channels.list');
      throw new Error(err);
    }

    const json = (await res.json()) as any;
    if (!json.items || json.items.length === 0) {
      throw new Error('No YouTube channel found for this Google account');
    }

    const item = json.items[0];
    const channelId = item.id;
    const snippet = item.snippet || {};
    const thumbnails = snippet.thumbnails || {};
    const avatarUrl = thumbnails.high?.url || thumbnails.medium?.url || thumbnails.default?.url || null;

    return {
      channelId,
      title: snippet.title || 'Unknown Channel',
      customUrl: snippet.customUrl || null,
      avatarUrl,
    };
  }

  /**
   * Queries authenticated user's own broadcasts via liveBroadcasts.list.
   * Valid filter parameters: broadcastStatus OR mine, never both, and never passing channelId as broadcastId.
   */
  public async checkAuthenticatedBroadcasts(
    accessToken: string,
    options?: { broadcastStatus?: 'active' | 'all' | 'completed' | 'upcoming'; mine?: boolean; broadcastId?: string }
  ): Promise<any[]> {
    if (options?.broadcastId) {
      if (options.broadcastId.startsWith('UC') || (options.broadcastId.length > 20 && options.broadcastId.includes('UC'))) {
        throw new Error(`Invalid broadcast ID '${options.broadcastId}': Channel ID must not be passed as broadcast ID`);
      }
    }

    let url: string;
    if (options?.broadcastId) {
      url = `https://www.googleapis.com/youtube/v3/liveBroadcasts?part=snippet,status&id=${encodeURIComponent(options.broadcastId)}`;
    } else if (options?.mine) {
      url = 'https://www.googleapis.com/youtube/v3/liveBroadcasts?part=snippet,status&mine=true';
    } else {
      const statusParam = options?.broadcastStatus || 'active';
      url = `https://www.googleapis.com/youtube/v3/liveBroadcasts?part=snippet,status&broadcastStatus=${encodeURIComponent(statusParam)}`;
    }

    const bRes = await fetch(url, {
      headers: {
        Authorization: `Bearer ${accessToken}`,
        Accept: 'application/json',
      },
    });

    if (!bRes.ok) {
      const err = await this.captureSanitizedError(bRes, 'liveBroadcasts.list');
      throw new Error(err);
    }

    const bData = (await bRes.json()) as any;
    return bData.items || [];
  }

  /**
   * Queries arbitrary linked channel live status using YouTube Data API search.list.
   */
  public async checkChannelViaSearch(
    channelId: string,
    auth?: { accessToken?: string; apiKey?: string }
  ): Promise<any[]> {
    const key = auth?.apiKey || this.apiKey;
    let url = `https://www.googleapis.com/youtube/v3/search?part=snippet&channelId=${encodeURIComponent(channelId)}&type=video&eventType=live&maxResults=1`;
    if (key) {
      url += `&key=${encodeURIComponent(key)}`;
    }

    const headers: Record<string, string> = {
      Accept: 'application/json',
    };
    if (auth?.accessToken) {
      headers['Authorization'] = `Bearer ${auth.accessToken}`;
    }

    const sRes = await fetch(url, { headers });
    if (!sRes.ok) {
      const err = await this.captureSanitizedError(sRes, 'search.list');
      throw new Error(err);
    }

    const sData = (await sRes.json()) as any;
    return sData.items || [];
  }

  /**
   * Queries video details (category, topic details, topicIds, and tags) for game classification.
   */
  public async getVideoMetadata(
    videoId: string,
    auth?: { accessToken?: string; apiKey?: string }
  ): Promise<{ categoryId: string; topicCategories: string[]; topicIds: string[]; tags: string[]; title: string }> {
    const key = auth?.apiKey || this.apiKey;
    let vUrl = `https://www.googleapis.com/youtube/v3/videos?part=snippet,topicDetails&id=${encodeURIComponent(videoId)}`;
    if (key) {
      vUrl += `&key=${encodeURIComponent(key)}`;
    }

    const headers: Record<string, string> = { Accept: 'application/json' };
    if (auth?.accessToken) {
      headers['Authorization'] = `Bearer ${auth.accessToken}`;
    }

    const vRes = await fetch(vUrl, { headers });
    let categoryId = '';
    let topicCategories: string[] = [];
    let topicIds: string[] = [];
    let tags: string[] = [];
    let title = '';

    if (vRes.ok) {
      const vData = (await vRes.json()) as any;
      if (vData.items && vData.items.length > 0) {
        const item = vData.items[0];
        categoryId = item.snippet?.categoryId || '';
        title = item.snippet?.title || '';
        tags = item.snippet?.tags || [];
        topicCategories = item.topicDetails?.topicCategories || [];
        const tIds = item.topicDetails?.topicIds || [];
        const relIds = item.topicDetails?.relevantTopicIds || [];
        topicIds = [...tIds, ...relIds];
      }
    } else {
      const err = await this.captureSanitizedError(vRes, 'videos.list');
      console.warn(`[YouTube] Failed to fetch video metadata: ${err}`);
    }

    return { categoryId, topicCategories, topicIds, tags, title };
  }

  /**
   * Fetches game/category title from YouTube watch page HTML/ytInitialData.
   * Zero quota cost, supports creator studio game selection even when topicCategories is unassigned or lagged.
   */
  public async fetchWatchPageGameTitle(videoId: string): Promise<string | null> {
    try {
      const controller = new AbortController();
      const timeout = setTimeout(() => controller.abort(), 3500);
      const res = await fetch(`https://www.youtube.com/watch?v=${encodeURIComponent(videoId)}`, {
        signal: controller.signal,
        headers: {
          'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
          'Accept-Language': 'en-US,en;q=0.9',
          'Accept': 'text/html',
        },
      });
      clearTimeout(timeout);
      if (!res.ok) return null;

      const html = await res.text();
      return extractGameTitleFromHtml(html);
    } catch {
      return null;
    }
  }

  /**
   * Checks the live broadcast status for a YouTube channel using centralized cache and quota controls.
   */
  public async checkChannelLiveStatus(channelId: string): Promise<LiveBroadcastResult> {
    const now = new Date();

    // 1. Check existing cache
    const cached = await db.getStreamCache(channelId);
    if (cached && new Date(cached.expires_at) > now) {
      return {
        status: cached.status,
        isLive: cached.is_live,
        isMinecraft: cached.is_minecraft,
        videoId: cached.video_id || undefined,
        title: cached.title || undefined,
        streamUrl: cached.stream_url || undefined,
        checkedAt: cached.checked_at,
        expiresAt: cached.expires_at,
        error: cached.last_error || undefined,
      };
    }

    // 2. Lookup account record to get refresh token
    const account = await db.getYouTubeAccount(channelId);

    if (!account && !this.apiKey) {
      return {
        status: 'OFFLINE',
        isLive: false,
        isMinecraft: false,
        checkedAt: now,
        expiresAt: new Date(now.getTime() + 60_000), // Sensible 60s cache
        error: 'Channel account not registered in MCast',
      };
    }

    // Handle accounts marked as requiring reauthorization
    if (account && (account.token_status === 'reauth_required' || account.token_status === 'revoked')) {
      const reconnectMsg = 'OAuth token expired or revoked. Reauthorization required: run /link youtube in Minecraft to reconnect.';
      this.logErrorThrottled(channelId, reconnectMsg);
      return {
        status: 'UNKNOWN',
        isLive: false,
        isMinecraft: false,
        checkedAt: now,
        expiresAt: new Date(now.getTime() + 60_000),
        error: reconnectMsg,
      };
    }

    let accessToken: string | undefined;
    if (account) {
      try {
        const refreshToken = decryptToken(account.encrypted_refresh_token, account.token_iv, account.token_auth_tag);
        accessToken = await this.getAccessTokenForChannel(channelId, refreshToken);
      } catch (err: any) {
        const sanitized = sanitizeApiError(err.message, 400);
        this.logErrorThrottled(channelId, `Failed to refresh token: ${sanitized}`);
        this.clearAccessTokenCache(channelId);

        const isRevoked = err.message?.includes('Reauthorization required') || err.message?.includes('invalid_grant');
        if (isRevoked) {
          await db.updateTokenStatus(channelId, 'reauth_required').catch(() => {});
        }

        const userError = isRevoked
          ? 'OAuth token expired or revoked. Reauthorization required: run /link youtube in Minecraft to reconnect.'
          : sanitized;

        if (cached) {
          return {
            status: cached.status,
            isLive: cached.is_live,
            isMinecraft: cached.is_minecraft,
            videoId: cached.video_id || undefined,
            title: cached.title || undefined,
            streamUrl: cached.stream_url || undefined,
            checkedAt: cached.checked_at,
            expiresAt: new Date(now.getTime() + 60_000),
            error: 'Stale: ' + userError,
          };
        }
        return {
          status: 'UNKNOWN',
          isLive: false,
          isMinecraft: false,
          checkedAt: now,
          expiresAt: new Date(now.getTime() + 60_000),
          error: userError,
        };
      }
    }

    try {
      let broadcastId: string | null = null;
      let broadcastTitle = '';

      if (accessToken) {
        try {
          // 3a. Check user's own broadcast using liveBroadcasts.list with valid broadcastStatus=active
          let items: any[];
          try {
            items = await this.checkAuthenticatedBroadcasts(accessToken, { broadcastStatus: 'active' });
          } catch (bErr: any) {
            // Handle expired access token (HTTP 401): clear cache, refresh once, and retry
            if (bErr.message?.includes('401') && account) {
              this.clearAccessTokenCache(channelId);
              const refreshToken = decryptToken(account.encrypted_refresh_token, account.token_iv, account.token_auth_tag);
              accessToken = await this.getAccessTokenForChannel(channelId, refreshToken);
              items = await this.checkAuthenticatedBroadcasts(accessToken, { broadcastStatus: 'active' });
            } else {
              throw bErr;
            }
          }

          if (items.length > 0) {
            broadcastId = items[0].id;
            broadcastTitle = items[0].snippet?.title || '';
          }
        } catch (authErr: any) {
          // If liveBroadcasts fails, fallback to search.list for this channel
          this.logErrorThrottled(channelId, `liveBroadcasts fallback to search: ${authErr.message}`);
          const searchItems = await this.checkChannelViaSearch(channelId, { accessToken, apiKey: this.apiKey });
          if (searchItems.length > 0) {
            broadcastId = searchItems[0].id?.videoId || null;
            broadcastTitle = searchItems[0].snippet?.title || '';
          }
        }
      } else {
        // 3b. Arbitrary channel without account: check via search.list with API key
        const searchItems = await this.checkChannelViaSearch(channelId, { apiKey: this.apiKey });
        if (searchItems.length > 0) {
          broadcastId = searchItems[0].id?.videoId || null;
          broadcastTitle = searchItems[0].snippet?.title || '';
        }
      }

      // If no active broadcast was found, channel is OFFLINE
      if (!broadcastId) {
        const result: LiveBroadcastResult = {
          status: 'OFFLINE',
          isLive: false,
          isMinecraft: false,
          checkedAt: now,
          expiresAt: new Date(now.getTime() + 60_000), // Sensible 60s cache
        };
        await this.persistCache(channelId, result);
        return result;
      }

      // 4. Query video metadata for Minecraft eligibility
      const streamUrl = `https://www.youtube.com/watch?v=${broadcastId}`;
      const videoMeta = await this.getVideoMetadata(broadcastId, { accessToken, apiKey: this.apiKey });
      const videoTitle = videoMeta.title || broadcastTitle;

      // Condition 2: YouTube's actual livestream title contains Minecraft (case-insensitive)
      const titleMentionsMinecraft = videoTitle.toLowerCase().includes('minecraft');

      // Condition 1: YouTube's actual livestream game/category metadata identifies Minecraft
      const hasMinecraftTopicWiki = videoMeta.topicCategories.some(t =>
        t.toLowerCase().includes('minecraft')
      );
      const hasMinecraftTopicId = (videoMeta.topicIds || []).some(id =>
        id.toLowerCase() === MINECRAFT_FREEBASE_MID.toLowerCase()
      );
      const isGaming = videoMeta.categoryId === GAMING_CATEGORY_ID;
      const hasMinecraftTag = isGaming && (videoMeta.tags || []).some(tag => {
        const lower = tag.toLowerCase().trim();
        return lower === 'minecraft' || lower.startsWith('minecraft ') || lower.endsWith(' minecraft');
      });

      // If game metadata is not verified yet from API, inspect the YouTube watch page HTML for the creator's selected game
      let watchPageGame: string | null = null;
      if (!hasMinecraftTopicWiki && !hasMinecraftTopicId && !hasMinecraftTag) {
        watchPageGame = await this.fetchWatchPageGameTitle(broadcastId);
      }
      const hasMinecraftGameTitle = !!watchPageGame && watchPageGame.toLowerCase().includes('minecraft');

      const hasMinecraftMetadata = hasMinecraftTopicWiki || hasMinecraftTopicId || hasMinecraftTag || hasMinecraftGameTitle;
      const isMinecraft = hasMinecraftMetadata || titleMentionsMinecraft;
      const status = isMinecraft ? 'LIVE_MINECRAFT' : 'LIVE_NOT_MINECRAFT';

      // Use a shorter cache TTL (30s) for LIVE_NOT_MINECRAFT so corrected stream metadata updates promptly
      const cacheTtlMs = status === 'LIVE_NOT_MINECRAFT' ? 30_000 : 60_000;

      const result: LiveBroadcastResult = {
        status,
        isLive: true,
        isMinecraft,
        videoId: broadcastId,
        title: videoTitle,
        streamUrl,
        checkedAt: now,
        expiresAt: new Date(now.getTime() + cacheTtlMs),
      };

      await this.persistCache(channelId, result);
      return result;
    } catch (err: any) {
      const sanitized = sanitizeApiError(err.message, 500);
      this.logErrorThrottled(channelId, `Error checking live status: ${sanitized}`);

      if (cached) {
        return {
          status: cached.status,
          isLive: cached.is_live,
          isMinecraft: cached.is_minecraft,
          videoId: cached.video_id || undefined,
          title: cached.title || undefined,
          streamUrl: cached.stream_url || undefined,
          checkedAt: cached.checked_at,
          expiresAt: new Date(now.getTime() + 60_000),
          error: 'Stale: ' + sanitized,
        };
      }

      const errResult: LiveBroadcastResult = {
        status: 'UNKNOWN',
        isLive: false,
        isMinecraft: false,
        checkedAt: now,
        expiresAt: new Date(now.getTime() + 60_000),
        error: sanitized,
      };
      await this.persistCache(channelId, errResult);
      return errResult;
    }
  }

  private async persistCache(channelId: string, result: LiveBroadcastResult): Promise<void> {
    const record: StreamCacheRecord = {
      channel_id: channelId,
      is_live: result.isLive,
      is_minecraft: result.isMinecraft,
      video_id: result.videoId || null,
      title: result.title || null,
      stream_url: result.streamUrl || null,
      status: result.status,
      checked_at: result.checkedAt,
      expires_at: result.expiresAt,
      last_error: result.error || null,
    };
    await db.upsertStreamCache(record);
  }
}

export const youtubeService = new YouTubeService();
