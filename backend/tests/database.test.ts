import { describe, it, expect, beforeEach } from 'vitest';
import { db } from '../src/db/index.js';

describe('Database Store Persistence Verification', () => {
  beforeEach(async () => {
    await db.init();
  });

  it('should expose database status and respond to ping', async () => {
    const status = db.getStatus();
    expect(status).toHaveProperty('engine');
    expect(['postgresql', 'in-memory']).toContain(status.engine);
    expect(status).toHaveProperty('connected');

    const ping = await db.ping();
    expect(ping.ok).toBe(true);
  });

  it('should store and retrieve server installations', async () => {
    const serverId = 'srv_persist_' + Math.random().toString(36).slice(2, 8);
    const created = await db.upsertServer({
      id: serverId,
      secret_hash: 'hash_1234567890abcdef',
      name: 'Persistence Survival Server',
      plugin_version: '1.2.0',
      paper_version: 'Paper 1.21.4',
      capabilities: ['nametag', 'chat'],
    });

    expect(created.id).toBe(serverId);
    expect(created.name).toBe('Persistence Survival Server');

    const fetched = await db.getServer(serverId);
    expect(fetched).not.toBeNull();
    expect(fetched?.id).toBe(serverId);
    expect(fetched?.name).toBe('Persistence Survival Server');

    // Test heartbeat update
    const updated = await db.updateHeartbeat(serverId);
    expect(updated).toBe(true);
  });

  it('should store and retrieve YouTube accounts with avatar_url and encrypted tokens', async () => {
    const channelId = 'UC_persist_' + Math.random().toString(36).slice(2, 8);
    const account = await db.upsertYouTubeAccount({
      channel_id: channelId,
      channel_title: 'Database Test Gamer',
      custom_url: '@dbtestgamer',
      avatar_url: 'https://yt3.ggpht.com/a/test-avatar-db.jpg',
      encrypted_refresh_token: 'enc_token_blob_123',
      token_iv: 'iv_123456789012',
      token_auth_tag: 'tag_123456789012',
      token_status: 'active',
      last_refreshed_at: new Date(),
    });

    expect(account.channel_id).toBe(channelId);
    expect(account.channel_title).toBe('Database Test Gamer');
    expect(account.avatar_url).toBe('https://yt3.ggpht.com/a/test-avatar-db.jpg');

    const fetched = await db.getYouTubeAccount(channelId);
    expect(fetched).not.toBeNull();
    expect(fetched?.channel_id).toBe(channelId);
    expect(fetched?.avatar_url).toBe('https://yt3.ggpht.com/a/test-avatar-db.jpg');
    expect(fetched?.custom_url).toBe('@dbtestgamer');
  });

  it('should store and retrieve player-channel links', async () => {
    const serverId = 'srv_link_' + Math.random().toString(36).slice(2, 8);
    const playerUuid = 'b1c2d3e4-f5a6-7b8c-9d0e-1f2a3b4c5d6e';

    const link = await db.linkPlayer({
      server_id: serverId,
      player_uuid: playerUuid,
      player_name: 'AlexPersist',
      youtube_channel_id: 'UC_test_link_channel',
      is_authorized: true,
    });

    expect(link.server_id).toBe(serverId);
    expect(link.player_uuid.toLowerCase()).toBe(playerUuid.toLowerCase());

    const retrieved = await db.getPlayerLink(serverId, playerUuid);
    expect(retrieved).not.toBeNull();
    expect(retrieved?.player_name).toBe('AlexPersist');
    expect(retrieved?.youtube_channel_id).toBe('UC_test_link_channel');

    const allLinks = await db.getLinksForServer(serverId);
    expect(allLinks.length).toBeGreaterThanOrEqual(1);

    const unlinked = await db.unlinkPlayer(serverId, playerUuid);
    expect(unlinked).toBe(true);

    const afterUnlink = await db.getPlayerLink(serverId, playerUuid);
    expect(afterUnlink).toBeNull();
  });

  it('should store single-use link sessions and handle completion lifecycle', async () => {
    const sessionId = 'c1d2e3f4-a5b6-7c8d-9e0f-1a2b3c4d5e6f';
    const code = 'DB' + Math.random().toString(36).slice(2, 6).toUpperCase();

    const created = await db.createLinkSession({
      id: sessionId,
      code,
      server_id: 'srv_session_test',
      player_uuid: 'd1e2f3a4-b5c6-7d8e-9f0a-1b2c3d4e5f6a',
      player_name: 'SessionTester',
      status: 'pending',
      youtube_channel_id: null,
      expires_at: new Date(Date.now() + 300000),
    });

    expect(created.code).toBe(code);
    expect(created.status).toBe('pending');

    const byCode = await db.getLinkSessionByCode(code);
    expect(byCode).not.toBeNull();
    expect(byCode?.id).toBe(sessionId);

    const byId = await db.getLinkSessionById(sessionId);
    expect(byId).not.toBeNull();
    expect(byId?.code).toBe(code);

    const completed = await db.completeLinkSession(sessionId, 'UC_linked_channel_999');
    expect(completed).toBe(true);

    const completedSession = await db.getLinkSessionById(sessionId);
    expect(completedSession?.status).toBe('completed');
    expect(completedSession?.youtube_channel_id).toBe('UC_linked_channel_999');
    expect(completedSession?.consumed_at).not.toBeNull();
  });

  it('should store and retrieve stream live cache', async () => {
    const channelId = 'UC_cache_' + Math.random().toString(36).slice(2, 8);
    const now = new Date();
    const expiresAt = new Date(Date.now() + 60000);

    await db.upsertStreamCache({
      channel_id: channelId,
      is_live: true,
      is_minecraft: true,
      video_id: 'vid_cache_test_123',
      title: 'Building Mega Castle in Hardcore',
      stream_url: 'https://youtube.com/watch?v=vid_cache_test_123',
      status: 'LIVE_MINECRAFT',
      checked_at: now,
      expires_at: expiresAt,
      last_error: null,
    });

    const cached = await db.getStreamCache(channelId);
    expect(cached).not.toBeNull();
    expect(cached?.is_live).toBe(true);
    expect(cached?.status).toBe('LIVE_MINECRAFT');
    expect(cached?.video_id).toBe('vid_cache_test_123');
    expect(cached?.title).toBe('Building Mega Castle in Hardcore');

    const multiCache = await db.getStreamCaches([channelId, 'non_existent_channel']);
    expect(multiCache.has(channelId)).toBe(true);
    expect(multiCache.get(channelId)?.status).toBe('LIVE_MINECRAFT');
  });
});
