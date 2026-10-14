import pg from 'pg';

export interface ServerRecord {
  id: string;
  secret_hash: string;
  name: string;
  plugin_version: string;
  paper_version: string;
  capabilities: string[];
  registered_at: Date;
  last_heartbeat_at: Date;
  is_revoked: boolean;
}

export interface YouTubeAccountRecord {
  channel_id: string;
  channel_title: string;
  custom_url: string | null;
  avatar_url?: string | null;
  encrypted_refresh_token: string;
  token_iv: string;
  token_auth_tag: string;
  token_status: string;
  last_refreshed_at: Date | null;
  created_at: Date;
  updated_at: Date;
}

export interface ServerPlayerLinkRecord {
  id?: number;
  server_id: string;
  player_uuid: string;
  player_name: string;
  youtube_channel_id: string;
  is_authorized: boolean;
  linked_at: Date;
  updated_at: Date;
}

export interface LinkSessionRecord {
  id: string;
  code: string;
  server_id: string;
  player_uuid: string;
  player_name: string;
  status: 'pending' | 'completed' | 'expired' | 'cancelled';
  youtube_channel_id: string | null;
  expires_at: Date;
  created_at: Date;
  consumed_at: Date | null;
}

export interface StreamCacheRecord {
  channel_id: string;
  is_live: boolean;
  is_minecraft: boolean;
  video_id: string | null;
  title: string | null;
  stream_url: string | null;
  status: 'LIVE_MINECRAFT' | 'LIVE_NOT_MINECRAFT' | 'OFFLINE' | 'UNKNOWN';
  checked_at: Date;
  expires_at: Date;
  last_error: string | null;
}

/**
 * Resilient Database Store supporting PostgreSQL and fast in-memory storage.
 */
class DatabaseStore {
  private pool: pg.Pool | null = null;
  private isConnectedToPostgres = false;
  private lastConnectionError: string | null = null;

  // In-memory backing stores
  private memServers = new Map<string, ServerRecord>();
  private memAccounts = new Map<string, YouTubeAccountRecord>();
  private memLinks = new Map<string, ServerPlayerLinkRecord>(); // key: `${server_id}:${player_uuid}`
  private memSessions = new Map<string, LinkSessionRecord>(); // key: id, and also indexed by code
  private memSessionsByCode = new Map<string, string>(); // code -> id
  private memStreamCache = new Map<string, StreamCacheRecord>();

  constructor() {
    const dbUrl = process.env.DATABASE_URL;
    if (dbUrl && !dbUrl.includes('localhost:5432/mcast_unreachable')) {
      if (dbUrl.includes('[YOUR-PASSWORD]') || dbUrl.includes('[YOUR_PASSWORD]')) {
        this.lastConnectionError = 'DATABASE_URL contains placeholder password [YOUR-PASSWORD]. Update with your real database password.';
        console.warn('[DB] ' + this.lastConnectionError + ' Operating in resilient in-memory mode.');
        return;
      }
      try {
        this.pool = new pg.Pool({
          connectionString: dbUrl,
          ssl: process.env.NODE_ENV === 'production' ? { rejectUnauthorized: false } : undefined,
          max: 10,
          idleTimeoutMillis: 30000,
          connectionTimeoutMillis: 4000,
        });
      } catch (err: any) {
        this.lastConnectionError = err?.message || 'Pool initialization failed';
        console.warn('[DB] PostgreSQL pool initialization failed, using in-memory store:', err);
      }
    } else {
      this.lastConnectionError = 'No DATABASE_URL configured';
    }
  }

  public async init(): Promise<void> {
    if (!this.pool) {
      if (!this.lastConnectionError) {
        this.lastConnectionError = 'DATABASE_URL not configured';
      }
      return;
    }
    try {
      const connectPromise = this.pool.connect();
      const timeoutPromise = new Promise<never>((_, reject) =>
        setTimeout(() => reject(new Error('PostgreSQL connection timed out (4s)')), 4000)
      );
      const client = await Promise.race([connectPromise, timeoutPromise]);
      try {
        await client.query('ALTER TABLE youtube_accounts ADD COLUMN IF NOT EXISTS avatar_url TEXT;');
      } catch (err: any) {
        console.warn('[DB] Non-fatal notice ensuring avatar_url column:', err.message);
      }
      client.release();
      this.isConnectedToPostgres = true;
      this.lastConnectionError = null;
      console.log('[DB] Connected to PostgreSQL successfully.');
    } catch (err: any) {
      this.lastConnectionError = err.message;
      console.warn('[DB] PostgreSQL unavailable (' + err.message + '). Operating in resilient in-memory mode.');
      this.isConnectedToPostgres = false;
    }
  }

  public isPostgres(): boolean {
    return this.isConnectedToPostgres;
  }

  public getStatus() {
    return {
      engine: this.isConnectedToPostgres ? ('postgresql' as const) : ('in-memory' as const),
      connected: this.isConnectedToPostgres,
      pool_initialized: !!this.pool,
      last_error: this.lastConnectionError,
    };
  }

  public async ping(): Promise<{ ok: boolean; latency_ms?: number; error?: string; in_memory_stats?: Record<string, number> }> {
    if (this.isConnectedToPostgres && this.pool) {
      const start = Date.now();
      try {
        await this.pool.query('SELECT 1;');
        return { ok: true, latency_ms: Date.now() - start };
      } catch (err: any) {
        return { ok: false, error: err.message };
      }
    }
    return {
      ok: true,
      in_memory_stats: {
        servers: this.memServers.size,
        accounts: this.memAccounts.size,
        links: this.memLinks.size,
        sessions: this.memSessions.size,
        stream_cache: this.memStreamCache.size,
      },
    };
  }

  // --- SERVERS ---

  public async upsertServer(server: Omit<ServerRecord, 'registered_at' | 'last_heartbeat_at' | 'is_revoked'>): Promise<ServerRecord> {
    const now = new Date();
    if (this.isConnectedToPostgres && this.pool) {
      const res = await this.pool.query(
        `INSERT INTO servers (id, secret_hash, name, plugin_version, paper_version, capabilities, registered_at, last_heartbeat_at, is_revoked)
         VALUES ($1, $2, $3, $4, $5, $6, $7, $7, false)
         ON CONFLICT (id) DO UPDATE SET
           name = EXCLUDED.name,
           plugin_version = EXCLUDED.plugin_version,
           paper_version = EXCLUDED.paper_version,
           capabilities = EXCLUDED.capabilities,
           last_heartbeat_at = EXCLUDED.last_heartbeat_at
         RETURNING *;`,
        [server.id, server.secret_hash, server.name, server.plugin_version, server.paper_version, JSON.stringify(server.capabilities), now]
      );
      return res.rows[0];
    }

    const existing = this.memServers.get(server.id);
    const record: ServerRecord = {
      ...server,
      registered_at: existing ? existing.registered_at : now,
      last_heartbeat_at: now,
      is_revoked: false,
    };
    this.memServers.set(server.id, record);
    return record;
  }

  public async getServer(id: string): Promise<ServerRecord | null> {
    if (this.isConnectedToPostgres && this.pool) {
      const res = await this.pool.query('SELECT * FROM servers WHERE id = $1', [id]);
      return res.rows[0] || null;
    }
    return this.memServers.get(id) || null;
  }

  public async updateHeartbeat(id: string): Promise<boolean> {
    const now = new Date();
    if (this.isConnectedToPostgres && this.pool) {
      const res = await this.pool.query('UPDATE servers SET last_heartbeat_at = $1 WHERE id = $2', [now, id]);
      return (res.rowCount ?? 0) > 0;
    }
    const s = this.memServers.get(id);
    if (!s) return false;
    s.last_heartbeat_at = now;
    return true;
  }

  // --- YOUTUBE ACCOUNTS ---

  public async upsertYouTubeAccount(account: Omit<YouTubeAccountRecord, 'created_at' | 'updated_at'>): Promise<YouTubeAccountRecord> {
    const now = new Date();
    if (this.isConnectedToPostgres && this.pool) {
      const res = await this.pool.query(
        `INSERT INTO youtube_accounts (channel_id, channel_title, custom_url, avatar_url, encrypted_refresh_token, token_iv, token_auth_tag, token_status, last_refreshed_at, created_at, updated_at)
         VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $10)
         ON CONFLICT (channel_id) DO UPDATE SET
           channel_title = EXCLUDED.channel_title,
           custom_url = EXCLUDED.custom_url,
           avatar_url = COALESCE(EXCLUDED.avatar_url, youtube_accounts.avatar_url),
           encrypted_refresh_token = EXCLUDED.encrypted_refresh_token,
           token_iv = EXCLUDED.token_iv,
           token_auth_tag = EXCLUDED.token_auth_tag,
           token_status = EXCLUDED.token_status,
           last_refreshed_at = EXCLUDED.last_refreshed_at,
           updated_at = EXCLUDED.updated_at
         RETURNING *;`,
        [account.channel_id, account.channel_title, account.custom_url, account.avatar_url || null, account.encrypted_refresh_token, account.token_iv, account.token_auth_tag, account.token_status, account.last_refreshed_at, now]
      );
      return res.rows[0];
    }

    const existing = this.memAccounts.get(account.channel_id);
    const rec: YouTubeAccountRecord = {
      ...account,
      avatar_url: account.avatar_url !== undefined ? account.avatar_url : (existing ? existing.avatar_url : null),
      created_at: existing ? existing.created_at : now,
      updated_at: now,
    };
    this.memAccounts.set(account.channel_id, rec);
    return rec;
  }

  public async getYouTubeAccount(channelId: string): Promise<YouTubeAccountRecord | null> {
    if (this.isConnectedToPostgres && this.pool) {
      const res = await this.pool.query('SELECT * FROM youtube_accounts WHERE channel_id = $1', [channelId]);
      return res.rows[0] || null;
    }
    return this.memAccounts.get(channelId) || null;
  }

  // --- SERVER PLAYER LINKS ---

  public async linkPlayer(link: Omit<ServerPlayerLinkRecord, 'id' | 'linked_at' | 'updated_at'>): Promise<ServerPlayerLinkRecord> {
    const now = new Date();
    if (this.isConnectedToPostgres && this.pool) {
      const res = await this.pool.query(
        `INSERT INTO server_player_links (server_id, player_uuid, player_name, youtube_channel_id, is_authorized, linked_at, updated_at)
         VALUES ($1, $2, $3, $4, $5, $6, $6)
         ON CONFLICT (server_id, player_uuid) DO UPDATE SET
           player_name = EXCLUDED.player_name,
           youtube_channel_id = EXCLUDED.youtube_channel_id,
           is_authorized = EXCLUDED.is_authorized,
           updated_at = EXCLUDED.updated_at
         RETURNING *;`,
        [link.server_id, link.player_uuid, link.player_name, link.youtube_channel_id, link.is_authorized, now]
      );
      return res.rows[0];
    }

    const key = `${link.server_id}:${link.player_uuid.toLowerCase()}`;
    const existing = this.memLinks.get(key);
    const rec: ServerPlayerLinkRecord = {
      ...link,
      player_uuid: link.player_uuid.toLowerCase(),
      linked_at: existing ? existing.linked_at : now,
      updated_at: now,
    };
    this.memLinks.set(key, rec);
    return rec;
  }

  public async unlinkPlayer(serverId: string, playerUuid: string): Promise<boolean> {
    if (this.isConnectedToPostgres && this.pool) {
      const res = await this.pool.query('DELETE FROM server_player_links WHERE server_id = $1 AND player_uuid = $2', [serverId, playerUuid]);
      return (res.rowCount ?? 0) > 0;
    }
    const key = `${serverId}:${playerUuid.toLowerCase()}`;
    return this.memLinks.delete(key);
  }

  public async getPlayerLink(serverId: string, playerUuid: string): Promise<ServerPlayerLinkRecord | null> {
    if (this.isConnectedToPostgres && this.pool) {
      const res = await this.pool.query('SELECT * FROM server_player_links WHERE server_id = $1 AND player_uuid = $2', [serverId, playerUuid]);
      return res.rows[0] || null;
    }
    const key = `${serverId}:${playerUuid.toLowerCase()}`;
    return this.memLinks.get(key) || null;
  }

  public async getLinksForServer(serverId: string, playerUuids?: string[]): Promise<ServerPlayerLinkRecord[]> {
    if (this.isConnectedToPostgres && this.pool) {
      if (playerUuids && playerUuids.length > 0) {
        const res = await this.pool.query(
          'SELECT * FROM server_player_links WHERE server_id = $1 AND player_uuid = ANY($2::uuid[])',
          [serverId, playerUuids]
        );
        return res.rows;
      }
      const res = await this.pool.query('SELECT * FROM server_player_links WHERE server_id = $1', [serverId]);
      return res.rows;
    }

    const results: ServerPlayerLinkRecord[] = [];
    const lowerSet = playerUuids ? new Set(playerUuids.map(u => u.toLowerCase())) : null;
    for (const [key, link] of this.memLinks.entries()) {
      if (link.server_id === serverId) {
        if (!lowerSet || lowerSet.has(link.player_uuid.toLowerCase())) {
          results.push(link);
        }
      }
    }
    return results;
  }

  // --- LINK SESSIONS ---

  public async createLinkSession(session: Omit<LinkSessionRecord, 'created_at' | 'consumed_at'>): Promise<LinkSessionRecord> {
    const now = new Date();
    const rec: LinkSessionRecord = {
      ...session,
      created_at: now,
      consumed_at: null,
    };

    if (this.isConnectedToPostgres && this.pool) {
      await this.pool.query(
        `INSERT INTO link_sessions (id, code, server_id, player_uuid, player_name, status, youtube_channel_id, expires_at, created_at)
         VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9);`,
        [rec.id, rec.code, rec.server_id, rec.player_uuid, rec.player_name, rec.status, rec.youtube_channel_id, rec.expires_at, now]
      );
      return rec;
    }

    this.memSessions.set(rec.id, rec);
    this.memSessionsByCode.set(rec.code, rec.id);
    return rec;
  }

  public async getLinkSessionById(id: string): Promise<LinkSessionRecord | null> {
    if (this.isConnectedToPostgres && this.pool) {
      const res = await this.pool.query('SELECT * FROM link_sessions WHERE id = $1', [id]);
      return res.rows[0] || null;
    }
    return this.memSessions.get(id) || null;
  }

  public async getLinkSessionByCode(code: string): Promise<LinkSessionRecord | null> {
    if (this.isConnectedToPostgres && this.pool) {
      const res = await this.pool.query('SELECT * FROM link_sessions WHERE code = $1', [code]);
      return res.rows[0] || null;
    }
    const id = this.memSessionsByCode.get(code);
    return id ? (this.memSessions.get(id) || null) : null;
  }

  public async completeLinkSession(id: string, channelId: string): Promise<boolean> {
    const now = new Date();
    if (this.isConnectedToPostgres && this.pool) {
      const res = await this.pool.query(
        `UPDATE link_sessions
         SET status = 'completed', youtube_channel_id = $1, consumed_at = $2
         WHERE id = $3 AND status = 'pending'`,
        [channelId, now, id]
      );
      return (res.rowCount ?? 0) > 0;
    }

    const s = this.memSessions.get(id);
    if (!s || s.status !== 'pending') return false;
    s.status = 'completed';
    s.youtube_channel_id = channelId;
    s.consumed_at = now;
    return true;
  }

  // --- STREAM CACHE ---

  public async upsertStreamCache(cache: StreamCacheRecord): Promise<void> {
    if (this.isConnectedToPostgres && this.pool) {
      await this.pool.query(
        `INSERT INTO stream_cache (channel_id, is_live, is_minecraft, video_id, title, stream_url, status, checked_at, expires_at, last_error)
         VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
         ON CONFLICT (channel_id) DO UPDATE SET
           is_live = EXCLUDED.is_live,
           is_minecraft = EXCLUDED.is_minecraft,
           video_id = EXCLUDED.video_id,
           title = EXCLUDED.title,
           stream_url = EXCLUDED.stream_url,
           status = EXCLUDED.status,
           checked_at = EXCLUDED.checked_at,
           expires_at = EXCLUDED.expires_at,
           last_error = EXCLUDED.last_error;`,
        [cache.channel_id, cache.is_live, cache.is_minecraft, cache.video_id, cache.title, cache.stream_url, cache.status, cache.checked_at, cache.expires_at, cache.last_error]
      );
      return;
    }
    this.memStreamCache.set(cache.channel_id, cache);
  }

  public async getStreamCache(channelId: string): Promise<StreamCacheRecord | null> {
    if (this.isConnectedToPostgres && this.pool) {
      const res = await this.pool.query('SELECT * FROM stream_cache WHERE channel_id = $1', [channelId]);
      return res.rows[0] || null;
    }
    return this.memStreamCache.get(channelId) || null;
  }

  public async getStreamCaches(channelIds: string[]): Promise<Map<string, StreamCacheRecord>> {
    const map = new Map<string, StreamCacheRecord>();
    if (channelIds.length === 0) return map;

    if (this.isConnectedToPostgres && this.pool) {
      const res = await this.pool.query('SELECT * FROM stream_cache WHERE channel_id = ANY($1::varchar[])', [channelIds]);
      for (const row of res.rows) {
        map.set(row.channel_id, row);
      }
      return map;
    }

    for (const cid of channelIds) {
      const rec = this.memStreamCache.get(cid);
      if (rec) map.set(cid, rec);
    }
    return map;
  }
}

export const db = new DatabaseStore();
