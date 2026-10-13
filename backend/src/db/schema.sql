-- ==============================================================================
-- MCast Central Backend Database Schema (PostgreSQL)
-- Supports multi-tenant server registration, centralized Google OAuth 2.0,
-- AES-256-GCM token encryption, single-use linking sessions, and stream caching.
-- ==============================================================================

-- 1. Servers Table (Registered Minecraft Paper Servers)
CREATE TABLE IF NOT EXISTS servers (
    id VARCHAR(64) PRIMARY KEY,
    secret_hash VARCHAR(128) NOT NULL,
    name VARCHAR(255) NOT NULL,
    plugin_version VARCHAR(32) DEFAULT '1.0.0',
    paper_version VARCHAR(64) DEFAULT 'Unknown',
    capabilities JSONB DEFAULT '[]'::jsonb,
    registered_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_heartbeat_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    is_revoked BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_servers_last_heartbeat ON servers(last_heartbeat_at);

-- 2. YouTube Accounts Table (Centralized Encrypted OAuth Credentials)
CREATE TABLE IF NOT EXISTS youtube_accounts (
    channel_id VARCHAR(64) PRIMARY KEY,
    channel_title VARCHAR(255) NOT NULL,
    custom_url VARCHAR(255),
    encrypted_refresh_token TEXT NOT NULL,
    token_iv VARCHAR(32) NOT NULL,
    token_auth_tag VARCHAR(32) NOT NULL,
    token_status VARCHAR(32) NOT NULL DEFAULT 'active',
    last_refreshed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 3. Server Player Links Table (Association between Minecraft Player & YouTube Channel)
CREATE TABLE IF NOT EXISTS server_player_links (
    id BIGSERIAL PRIMARY KEY,
    server_id VARCHAR(64) NOT NULL REFERENCES servers(id) ON DELETE CASCADE,
    player_uuid UUID NOT NULL,
    player_name VARCHAR(64) NOT NULL,
    youtube_channel_id VARCHAR(64) NOT NULL REFERENCES youtube_accounts(channel_id) ON DELETE CASCADE,
    is_authorized BOOLEAN NOT NULL DEFAULT TRUE,
    linked_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_server_player UNIQUE (server_id, player_uuid)
);

CREATE INDEX IF NOT EXISTS idx_server_player_links_lookup ON server_player_links(server_id, player_uuid);
CREATE INDEX IF NOT EXISTS idx_server_player_links_channel ON server_player_links(youtube_channel_id);

-- 4. Single-Use Link Sessions Table (Short-lived codes for /link youtube)
CREATE TABLE IF NOT EXISTS link_sessions (
    id UUID PRIMARY KEY,
    code VARCHAR(32) UNIQUE NOT NULL,
    server_id VARCHAR(64) NOT NULL REFERENCES servers(id) ON DELETE CASCADE,
    player_uuid UUID NOT NULL,
    player_name VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'pending', -- pending, completed, expired, cancelled
    youtube_channel_id VARCHAR(64),
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    consumed_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_link_sessions_code ON link_sessions(code);
CREATE INDEX IF NOT EXISTS idx_link_sessions_server_player ON link_sessions(server_id, player_uuid);
CREATE INDEX IF NOT EXISTS idx_link_sessions_expires_at ON link_sessions(expires_at);

-- 5. Central Stream Cache Table (Deduplicated Live Status for YouTube Channels)
CREATE TABLE IF NOT EXISTS stream_cache (
    channel_id VARCHAR(64) PRIMARY KEY REFERENCES youtube_accounts(channel_id) ON DELETE CASCADE,
    is_live BOOLEAN NOT NULL DEFAULT FALSE,
    is_minecraft BOOLEAN NOT NULL DEFAULT FALSE,
    video_id VARCHAR(32),
    title TEXT,
    stream_url TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'OFFLINE', -- LIVE_MINECRAFT, LIVE_NOT_MINECRAFT, OFFLINE, UNKNOWN
    checked_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL,
    last_error TEXT
);

CREATE INDEX IF NOT EXISTS idx_stream_cache_expires_at ON stream_cache(expires_at);
