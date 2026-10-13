import { db, StreamCacheRecord } from '../db/index.js';
import { decryptToken } from '../crypto/encryption.js';

export const GAMING_CATEGORY_ID = '20';
export const MINECRAFT_TOPIC_WIKI = 'https://en.wikipedia.org/wiki/Minecraft';

export interface ChannelProfile {
  channelId: string;
  title: string;
  customUrl: string | null;
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

export class YouTubeService {
  private clientId: string;
  private clientSecret: string;
  private redirectUri: string;

  constructor() {
    this.clientId = process.env.GOOGLE_CLIENT_ID || '';
    this.clientSecret = process.env.GOOGLE_CLIENT_SECRET || '';
    this.redirectUri = process.env.GOOGLE_REDIRECT_URI || 'https://api.mcast.fun/v1/oauth/callback';
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
      throw new Error(`Google token exchange failed (${res.status}): ${errText}`);
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
      throw new Error(`Google token refresh failed (${res.status}): ${errText}`);
    }

    const data = (await res.json()) as any;
    return {
      accessToken: data.access_token,
      expiresIn: data.expires_in || 3600,
    };
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
      throw new Error(`YouTube channels.list failed with HTTP ${res.status}`);
    }

    const json = (await res.json()) as any;
    if (!json.items || json.items.length === 0) {
      throw new Error('No YouTube channel found for this Google account');
    }

    const item = json.items[0];
    const channelId = item.id;
    const snippet = item.snippet || {};

    return {
      channelId,
      title: snippet.title || 'Unknown Channel',
      customUrl: snippet.customUrl || null,
    };
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
    if (!account) {
      return {
        status: 'OFFLINE',
        isLive: false,
        isMinecraft: false,
        checkedAt: now,
        expiresAt: new Date(now.getTime() + 60_000),
        error: 'Channel account not registered in MCast',
      };
    }

    // 3. Decrypt refresh token and obtain fresh access token
    let accessToken: string;
    try {
      const refreshToken = decryptToken(account.encrypted_refresh_token, account.token_iv, account.token_auth_tag);
      const tokenResult = await this.refreshAccessToken(refreshToken);
      accessToken = tokenResult.accessToken;
    } catch (err: any) {
      console.warn(`[YouTube] Failed to refresh token for channel ${channelId}:`, err.message);
      // Retain stale status if available during temporary Google token error
      if (cached) {
        return {
          status: cached.status,
          isLive: cached.is_live,
          isMinecraft: cached.is_minecraft,
          videoId: cached.video_id || undefined,
          title: cached.title || undefined,
          streamUrl: cached.stream_url || undefined,
          checkedAt: cached.checked_at,
          expiresAt: new Date(now.getTime() + 30_000),
          error: 'Stale: Refresh token temporarily rejected',
        };
      }
      return {
        status: 'UNKNOWN',
        isLive: false,
        isMinecraft: false,
        checkedAt: now,
        expiresAt: new Date(now.getTime() + 30_000),
        error: err.message,
      };
    }

    // 4. Query active live broadcasts (1 quota unit)
    try {
      const activeUrl = 'https://www.googleapis.com/youtube/v3/liveBroadcasts?part=snippet,status&broadcastStatus=active&mine=true';
      const bRes = await fetch(activeUrl, {
        headers: {
          Authorization: `Bearer ${accessToken}`,
          Accept: 'application/json',
        },
      });

      if (!bRes.ok) {
        throw new Error(`liveBroadcasts.list failed with HTTP ${bRes.status}`);
      }

      const bData = (await bRes.json()) as any;
      const items = bData.items || [];

      if (items.length === 0) {
        const result: LiveBroadcastResult = {
          status: 'OFFLINE',
          isLive: false,
          isMinecraft: false,
          checkedAt: now,
          expiresAt: new Date(now.getTime() + 60_000), // Cache 60s
        };
        await this.persistCache(channelId, result);
        return result;
      }

      const activeBroadcast = items[0];
      const broadcastId = activeBroadcast.id;
      const broadcastTitle = activeBroadcast.snippet?.title || '';
      const streamUrl = `https://www.youtube.com/watch?v=${broadcastId}`;

      // 5. Query video details for categoryId and topicDetails (1 quota unit)
      const vUrl = `https://www.googleapis.com/youtube/v3/videos?part=snippet,topicDetails&id=${encodeURIComponent(broadcastId)}`;
      const vRes = await fetch(vUrl, {
        headers: {
          Authorization: `Bearer ${accessToken}`,
          Accept: 'application/json',
        },
      });

      let categoryId = '';
      let topicCategories: string[] = [];
      let videoTitle = broadcastTitle;

      if (vRes.ok) {
        const vData = (await vRes.json()) as any;
        if (vData.items && vData.items.length > 0) {
          const vItem = vData.items[0];
          categoryId = vItem.snippet?.categoryId || '';
          videoTitle = vItem.snippet?.title || broadcastTitle;
          topicCategories = vItem.topicDetails?.topicCategories || [];
        }
      }

      // 6. Eligibility Verification Logic
      const isGaming = categoryId === GAMING_CATEGORY_ID;
      const hasMinecraftTopic = topicCategories.some(t =>
        t.toLowerCase() === MINECRAFT_TOPIC_WIKI.toLowerCase() || t.toLowerCase().endsWith('/minecraft')
      );
      const titleMentionsMinecraft = videoTitle.toLowerCase().includes('minecraft');

      const isMinecraft = hasMinecraftTopic || (isGaming && titleMentionsMinecraft);
      const status = isMinecraft ? 'LIVE_MINECRAFT' : (isGaming ? 'LIVE_NOT_MINECRAFT' : 'LIVE_NOT_MINECRAFT');

      const result: LiveBroadcastResult = {
        status,
        isLive: true,
        isMinecraft,
        videoId: broadcastId,
        title: videoTitle,
        streamUrl,
        checkedAt: now,
        expiresAt: new Date(now.getTime() + 60_000), // Cache 60s
      };

      await this.persistCache(channelId, result);
      return result;
    } catch (err: any) {
      console.warn(`[YouTube] Error checking live broadcasts for ${channelId}:`, err.message);
      if (cached) {
        return {
          status: cached.status,
          isLive: cached.is_live,
          isMinecraft: cached.is_minecraft,
          videoId: cached.video_id || undefined,
          title: cached.title || undefined,
          streamUrl: cached.stream_url || undefined,
          checkedAt: cached.checked_at,
          expiresAt: new Date(now.getTime() + 30_000),
          error: 'Stale: ' + err.message,
        };
      }
      return {
        status: 'UNKNOWN',
        isLive: false,
        isMinecraft: false,
        checkedAt: now,
        expiresAt: new Date(now.getTime() + 30_000),
        error: err.message,
      };
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
