import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { YouTubeService, sanitizeApiError, GAMING_CATEGORY_ID, MINECRAFT_TOPIC_WIKI } from '../src/services/youtube.service.js';
import { db } from '../src/db/index.js';
import { encryptToken } from '../src/crypto/encryption.js';

describe('YouTube Service & Live-Status Checker', () => {
  let service: YouTubeService;
  const originalFetch = global.fetch;

  beforeEach(() => {
    service = new YouTubeService();
    vi.restoreAllMocks();
  });

  afterEach(() => {
    global.fetch = originalFetch;
  });

  describe('1. Error Sanitization & Credential Protection', () => {
    it('should parse YouTube API JSON error and extract reason and message', () => {
      const youtubeErrorJson = JSON.stringify({
        error: {
          code: 400,
          message: 'Incompatible parameters: broadcastStatus, mine',
          errors: [
            {
              message: 'Incompatible parameters: broadcastStatus, mine',
              domain: 'youtube.parameter',
              reason: 'incompatibleParameters',
            },
          ],
        },
      });

      const sanitized = sanitizeApiError(youtubeErrorJson, 400);
      expect(sanitized).toBe('HTTP 400 ([incompatibleParameters]: Incompatible parameters: broadcastStatus, mine)');
      expect(sanitized).not.toContain('error":');
    });

    it('should redact sensitive OAuth tokens, client secrets, and bearer headers', () => {
      const leakPayload = 'Error occurred with Bearer ya29.a0AfH6SMB... and client_secret=GOCSPX-secret123 and refresh_token=1//04xyz';
      const sanitized = sanitizeApiError(leakPayload, 500);

      expect(sanitized).not.toContain('ya29.');
      expect(sanitized).not.toContain('GOCSPX-secret123');
      expect(sanitized).not.toContain('1//04xyz');
      expect(sanitized).toContain('Bearer [REDACTED]');
      expect(sanitized).toContain('client_secret=[REDACTED]');
      expect(sanitized).toContain('refresh_token=[REDACTED]');
    });

    it('should handle empty or whitespace error bodies safely', () => {
      expect(sanitizeApiError('', 400)).toBe('HTTP 400');
      expect(sanitizeApiError('   ', 503)).toBe('HTTP 503');
    });
  });

  describe('2. Parameter Validation & Channel ID Guard for liveBroadcasts.list', () => {
    it('should query liveBroadcasts.list with valid broadcastStatus=active without mine=true', async () => {
      let requestedUrl = '';
      let authHeader = '';

      global.fetch = vi.fn(async (url: any, init: any) => {
        requestedUrl = String(url);
        authHeader = init?.headers?.Authorization;
        return new Response(JSON.stringify({ items: [] }), { status: 200 });
      });

      const items = await service.checkAuthenticatedBroadcasts('test_access_token', { broadcastStatus: 'active' });
      expect(items).toEqual([]);
      expect(requestedUrl).toBe('https://www.googleapis.com/youtube/v3/liveBroadcasts?part=snippet,status&broadcastStatus=active');
      expect(requestedUrl).not.toContain('mine=true');
      expect(authHeader).toBe('Bearer test_access_token');
    });

    it('should allow mine=true when explicitly requested without broadcastStatus', async () => {
      let requestedUrl = '';

      global.fetch = vi.fn(async (url: any) => {
        requestedUrl = String(url);
        return new Response(JSON.stringify({ items: [] }), { status: 200 });
      });

      await service.checkAuthenticatedBroadcasts('test_access_token', { mine: true });
      expect(requestedUrl).toBe('https://www.googleapis.com/youtube/v3/liveBroadcasts?part=snippet,status&mine=true');
      expect(requestedUrl).not.toContain('broadcastStatus');
    });

    it('should reject passing a YouTube Channel ID as broadcast ID to liveBroadcasts.list', async () => {
      const channelId = 'UC_x5XG1OV2P6uZZ5FSM9Ttw';

      await expect(
        service.checkAuthenticatedBroadcasts('test_token', { broadcastId: channelId })
      ).rejects.toThrow(/Channel ID must not be passed as broadcast ID/);
    });
  });

  describe('3. Arbitrary Channel Checking via search.list', () => {
    it('should use search.list with channelId, type=video, and eventType=live', async () => {
      let requestedUrl = '';
      let authHeader = '';
      const channelId = 'UC_x5XG1OV2P6uZZ5FSM9Ttw';

      global.fetch = vi.fn(async (url: any, init: any) => {
        requestedUrl = String(url);
        authHeader = init?.headers?.Authorization;
        return new Response(
          JSON.stringify({
            items: [
              {
                id: { videoId: 'live_vid_123' },
                snippet: { title: 'Live Streaming Minecraft' },
              },
            ],
          }),
          { status: 200 }
        );
      });

      const items = await service.checkChannelViaSearch(channelId, {
        accessToken: 'bearer_token_abc',
        apiKey: 'test_api_key',
      });

      expect(items.length).toBe(1);
      expect(items[0].id.videoId).toBe('live_vid_123');
      expect(requestedUrl).toContain('channelId=' + encodeURIComponent(channelId));
      expect(requestedUrl).toContain('type=video');
      expect(requestedUrl).toContain('eventType=live');
      expect(requestedUrl).toContain('key=test_api_key');
      expect(authHeader).toBe('Bearer bearer_token_abc');
    });
  });

  describe('4. Live & Offline Channel Status Detection', () => {
    const testChannelId = 'UC_test_offline_and_live_' + Math.random().toString(36).slice(2, 8);

    beforeEach(async () => {
      // Register test account in database with mock encrypted refresh token
      const enc = encryptToken('mock_refresh_token_12345');
      await db.upsertYouTubeAccount({
        channel_id: testChannelId,
        channel_title: 'Test Streamer',
        custom_url: '@teststreamer',
        encrypted_refresh_token: enc.ciphertext,
        token_iv: enc.iv,
        token_auth_tag: enc.authTag,
        token_status: 'active',
        last_refreshed_at: new Date(),
      });
    });

    it('should report OFFLINE status when channel has no active live streams', async () => {
      global.fetch = vi.fn(async (url: any) => {
        const urlStr = String(url);
        if (urlStr.includes('oauth2.googleapis.com/token')) {
          return new Response(JSON.stringify({ access_token: 'fresh_mock_token', expires_in: 3600 }), { status: 200 });
        }
        if (urlStr.includes('liveBroadcasts')) {
          // No active broadcasts
          return new Response(JSON.stringify({ items: [] }), { status: 200 });
        }
        return new Response('Not found', { status: 404 });
      });

      const result = await service.checkChannelLiveStatus(testChannelId);

      expect(result.status).toBe('OFFLINE');
      expect(result.isLive).toBe(false);
      expect(result.isMinecraft).toBe(false);
      expect(result.videoId).toBeUndefined();
      expect(result.expiresAt.getTime() - result.checkedAt.getTime()).toBeGreaterThanOrEqual(60_000);
    });

    it('should report LIVE_MINECRAFT status when active Minecraft stream is found', async () => {
      const liveChannelId = 'UC_live_minecraft_' + Math.random().toString(36).slice(2, 8);
      const enc = encryptToken('mock_refresh_live');
      await db.upsertYouTubeAccount({
        channel_id: liveChannelId,
        channel_title: 'Minecraft Pro',
        custom_url: '@mcpro',
        encrypted_refresh_token: enc.ciphertext,
        token_iv: enc.iv,
        token_auth_tag: enc.authTag,
        token_status: 'active',
        last_refreshed_at: new Date(),
      });

      global.fetch = vi.fn(async (url: any) => {
        const urlStr = String(url);
        if (urlStr.includes('oauth2.googleapis.com/token')) {
          return new Response(JSON.stringify({ access_token: 'valid_token_xyz', expires_in: 3600 }), { status: 200 });
        }
        if (urlStr.includes('liveBroadcasts')) {
          return new Response(
            JSON.stringify({
              items: [
                {
                  id: 'vid_mc_stream_99',
                  snippet: { title: 'Hardcore Minecraft 100 Days Live' },
                },
              ],
            }),
            { status: 200 }
          );
        }
        if (urlStr.includes('videos?')) {
          return new Response(
            JSON.stringify({
              items: [
                {
                  snippet: {
                    title: 'Hardcore Minecraft 100 Days Live',
                    categoryId: GAMING_CATEGORY_ID,
                  },
                  topicDetails: {
                    topicCategories: [MINECRAFT_TOPIC_WIKI],
                  },
                },
              ],
            }),
            { status: 200 }
          );
        }
        return new Response('Not found', { status: 404 });
      });

      const result = await service.checkChannelLiveStatus(liveChannelId);

      expect(result.status).toBe('LIVE_MINECRAFT');
      expect(result.isLive).toBe(true);
      expect(result.isMinecraft).toBe(true);
      expect(result.videoId).toBe('vid_mc_stream_99');
      expect(result.title).toBe('Hardcore Minecraft 100 Days Live');
      expect(result.streamUrl).toBe('https://www.youtube.com/watch?v=vid_mc_stream_99');
      expect(result.expiresAt.getTime() - result.checkedAt.getTime()).toBeGreaterThanOrEqual(60_000);
    });

    it('should report LIVE_NOT_MINECRAFT status when streaming another game', async () => {
      const varietyChannelId = 'UC_variety_' + Math.random().toString(36).slice(2, 8);
      const enc = encryptToken('mock_refresh_variety');
      await db.upsertYouTubeAccount({
        channel_id: varietyChannelId,
        channel_title: 'Variety Streamer',
        custom_url: '@variety',
        encrypted_refresh_token: enc.ciphertext,
        token_iv: enc.iv,
        token_auth_tag: enc.authTag,
        token_status: 'active',
        last_refreshed_at: new Date(),
      });

      global.fetch = vi.fn(async (url: any) => {
        const urlStr = String(url);
        if (urlStr.includes('oauth2.googleapis.com/token')) {
          return new Response(JSON.stringify({ access_token: 'valid_token_xyz', expires_in: 3600 }), { status: 200 });
        }
        if (urlStr.includes('liveBroadcasts')) {
          return new Response(
            JSON.stringify({
              items: [
                {
                  id: 'vid_chess_123',
                  snippet: { title: 'Speed Chess Championship' },
                },
              ],
            }),
            { status: 200 }
          );
        }
        if (urlStr.includes('videos?')) {
          return new Response(
            JSON.stringify({
              items: [
                {
                  snippet: {
                    title: 'Speed Chess Championship',
                    categoryId: GAMING_CATEGORY_ID,
                  },
                  topicDetails: {
                    topicCategories: ['https://en.wikipedia.org/wiki/Chess'],
                  },
                },
              ],
            }),
            { status: 200 }
          );
        }
        return new Response('Not found', { status: 404 });
      });

      const result = await service.checkChannelLiveStatus(varietyChannelId);

      expect(result.status).toBe('LIVE_NOT_MINECRAFT');
      expect(result.isLive).toBe(true);
      expect(result.isMinecraft).toBe(false);
      expect(result.videoId).toBe('vid_chess_123');
    });
  });

  describe('5. Expired Token Handling & Automatic Refresh Retry', () => {
    it('should retry after refreshing access token when YouTube returns HTTP 401', async () => {
      const retryChannelId = 'UC_retry_401_' + Math.random().toString(36).slice(2, 8);
      const enc = encryptToken('mock_refresh_retry');
      await db.upsertYouTubeAccount({
        channel_id: retryChannelId,
        channel_title: 'Retry Channel',
        custom_url: null,
        encrypted_refresh_token: enc.ciphertext,
        token_iv: enc.iv,
        token_auth_tag: enc.authTag,
        token_status: 'active',
        last_refreshed_at: new Date(),
      });

      let tokenFetchCount = 0;
      let broadcastCallCount = 0;

      global.fetch = vi.fn(async (url: any, init: any) => {
        const urlStr = String(url);
        if (urlStr.includes('oauth2.googleapis.com/token')) {
          tokenFetchCount++;
          return new Response(JSON.stringify({ access_token: `token_v${tokenFetchCount}`, expires_in: 3600 }), { status: 200 });
        }
        if (urlStr.includes('liveBroadcasts')) {
          broadcastCallCount++;
          if (broadcastCallCount === 1) {
            // First attempt: simulate expired token with 401
            return new Response(
              JSON.stringify({
                error: { code: 401, message: 'Request had invalid authentication credentials.' },
              }),
              { status: 401 }
            );
          }
          // Second attempt with refreshed token: succeeds
          expect(init?.headers?.Authorization).toBe('Bearer token_v2');
          return new Response(JSON.stringify({ items: [] }), { status: 200 });
        }
        return new Response('Not found', { status: 404 });
      });

      const result = await service.checkChannelLiveStatus(retryChannelId);

      expect(result.status).toBe('OFFLINE');
      expect(tokenFetchCount).toBe(2);
      expect(broadcastCallCount).toBe(2);
    });
  });

  describe('6. Polling Interval & Repeated Error Log Suppression', () => {
    it('should suppress duplicate warning logs for the same channel within suppression interval', () => {
      const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});
      const channelId = 'UC_repeated_err';
      const errMsg = 'liveBroadcasts.list failed with HTTP 400 ([incompatibleParameters]: Incompatible parameters: broadcastStatus, mine)';

      // First occurrence: should log
      service.logErrorThrottled(channelId, errMsg);
      expect(warnSpy).toHaveBeenCalledTimes(1);

      // Repeated occurrence 30 seconds later: should be suppressed
      service.logErrorThrottled(channelId, errMsg);
      service.logErrorThrottled(channelId, errMsg);
      expect(warnSpy).toHaveBeenCalledTimes(1);

      // Different error message: should log immediately
      service.logErrorThrottled(channelId, 'Another distinct error message');
      expect(warnSpy).toHaveBeenCalledTimes(2);

      warnSpy.mockRestore();
    });
  });
});
