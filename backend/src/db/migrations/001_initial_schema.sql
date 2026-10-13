-- ==============================================================================
-- Supabase Migration: 001_initial_schema.sql
-- MCast Central Backend: Multi-Tenant Architecture & Security Policies
-- ==============================================================================

-- 1. SERVERS TABLE
-- Stores registered Minecraft Paper server installations.
CREATE TABLE IF NOT EXISTS public.servers (
    id VARCHAR(64) PRIMARY KEY,
    secret_hash VARCHAR(128) NOT NULL,
    name VARCHAR(255) NOT NULL DEFAULT 'Minecraft Server',
    plugin_version VARCHAR(32) NOT NULL DEFAULT '1.0.0',
    paper_version VARCHAR(64) NOT NULL DEFAULT 'Unknown',
    capabilities JSONB NOT NULL DEFAULT '[]'::jsonb,
    registered_at TIMESTAMPTZ NOT NULL DEFAULT TIMEZONE('utc', NOW()),
    last_heartbeat_at TIMESTAMPTZ NOT NULL DEFAULT TIMEZONE('utc', NOW()),
    is_revoked BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_servers_last_heartbeat ON public.servers(last_heartbeat_at);
CREATE INDEX IF NOT EXISTS idx_servers_revoked ON public.servers(is_revoked);

-- 2. YOUTUBE ACCOUNTS TABLE
-- Confidential Google OAuth credentials. Refresh tokens encrypted at rest with AES-256-GCM.
CREATE TABLE IF NOT EXISTS public.youtube_accounts (
    channel_id VARCHAR(64) PRIMARY KEY,
    channel_title VARCHAR(255) NOT NULL,
    custom_url VARCHAR(255),
    encrypted_refresh_token TEXT NOT NULL,
    token_iv VARCHAR(32) NOT NULL,
    token_auth_tag VARCHAR(32) NOT NULL,
    token_status VARCHAR(32) NOT NULL DEFAULT 'active',
    last_refreshed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT TIMEZONE('utc', NOW()),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT TIMEZONE('utc', NOW())
);

-- 3. SERVER PLAYER LINKS TABLE
-- Multi-tenant association between Minecraft players and their authorized YouTube channel.
CREATE TABLE IF NOT EXISTS public.server_player_links (
    id BIGSERIAL PRIMARY KEY,
    server_id VARCHAR(64) NOT NULL REFERENCES public.servers(id) ON DELETE CASCADE,
    player_uuid UUID NOT NULL,
    player_name VARCHAR(64) NOT NULL,
    youtube_channel_id VARCHAR(64) NOT NULL REFERENCES public.youtube_accounts(channel_id) ON DELETE CASCADE,
    is_authorized BOOLEAN NOT NULL DEFAULT TRUE,
    linked_at TIMESTAMPTZ NOT NULL DEFAULT TIMEZONE('utc', NOW()),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT TIMEZONE('utc', NOW()),
    CONSTRAINT uq_server_player UNIQUE (server_id, player_uuid)
);

CREATE INDEX IF NOT EXISTS idx_server_player_links_lookup ON public.server_player_links(server_id, player_uuid);
CREATE INDEX IF NOT EXISTS idx_server_player_links_channel ON public.server_player_links(youtube_channel_id);

-- 4. LINK SESSIONS TABLE
-- Cryptographically random single-use codes (5-minute TTL) for /link youtube.
CREATE TABLE IF NOT EXISTS public.link_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(32) UNIQUE NOT NULL,
    server_id VARCHAR(64) NOT NULL REFERENCES public.servers(id) ON DELETE CASCADE,
    player_uuid UUID NOT NULL,
    player_name VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'pending', -- 'pending', 'completed', 'expired', 'cancelled'
    youtube_channel_id VARCHAR(64) REFERENCES public.youtube_accounts(channel_id) ON DELETE SET NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT TIMEZONE('utc', NOW()),
    consumed_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_link_sessions_code ON public.link_sessions(code);
CREATE INDEX IF NOT EXISTS idx_link_sessions_server_player ON public.link_sessions(server_id, player_uuid);
CREATE INDEX IF NOT EXISTS idx_link_sessions_expires_at ON public.link_sessions(expires_at);

-- 5. STREAM CACHE TABLE
-- Shared channel-level status cache with 60s TTL to protect YouTube Data API v3 quota.
CREATE TABLE IF NOT EXISTS public.stream_cache (
    channel_id VARCHAR(64) PRIMARY KEY REFERENCES public.youtube_accounts(channel_id) ON DELETE CASCADE,
    is_live BOOLEAN NOT NULL DEFAULT FALSE,
    is_minecraft BOOLEAN NOT NULL DEFAULT FALSE,
    video_id VARCHAR(32),
    title TEXT,
    stream_url TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'OFFLINE', -- 'LIVE_MINECRAFT', 'LIVE_NOT_MINECRAFT', 'OFFLINE', 'UNKNOWN'
    checked_at TIMESTAMPTZ NOT NULL DEFAULT TIMEZONE('utc', NOW()),
    expires_at TIMESTAMPTZ NOT NULL,
    last_error TEXT
);

CREATE INDEX IF NOT EXISTS idx_stream_cache_expires ON public.stream_cache(expires_at);

-- ==============================================================================
-- SUPABASE ROW LEVEL SECURITY (RLS) POLICIES
-- Ensures public browser sessions cannot read server credentials or tokens.
-- ==============================================================================

ALTER TABLE public.servers ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.youtube_accounts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.server_player_links ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.link_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.stream_cache ENABLE ROW LEVEL SECURITY;

-- Servers: Accessible only via service_role (Render backend)
CREATE POLICY "Servers service role only" ON public.servers
    FOR ALL USING (auth.role() = 'service_role');

-- YouTube Accounts: Strictly service role only. Encrypted refresh tokens never public.
CREATE POLICY "Accounts service role only" ON public.youtube_accounts
    FOR ALL USING (auth.role() = 'service_role');

-- Player Links: Strictly service role only.
CREATE POLICY "Links service role only" ON public.server_player_links
    FOR ALL USING (auth.role() = 'service_role');

-- Link Sessions: Backend has full access; anonymous website users can query pending code details
CREATE POLICY "Link sessions backend access" ON public.link_sessions
    FOR ALL USING (auth.role() = 'service_role');

CREATE POLICY "Link sessions public view code" ON public.link_sessions
    FOR SELECT USING (status = 'pending' AND expires_at > NOW());

-- Stream Cache: Public read access for frontend widgets; backend writes
CREATE POLICY "Stream cache public read" ON public.stream_cache
    FOR SELECT USING (true);

CREATE POLICY "Stream cache backend write" ON public.stream_cache
    FOR ALL USING (auth.role() = 'service_role');
