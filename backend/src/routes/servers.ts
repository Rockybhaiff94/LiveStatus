import { Router, Response } from 'express';
import crypto from 'node:crypto';
import { z } from 'zod';
import { db } from '../db/index.js';
import { hashServerSecret } from '../crypto/encryption.js';
import { requireServerAuth, AuthenticatedServerRequest } from '../middleware/auth.js';
import { youtubeService } from '../services/youtube.service.js';

export const serverRouter = Router();

// Validation Schemas
const registerSchema = z.object({
  server_id: z.string().min(8).max(64),
  server_secret: z.string().min(32).max(128),
  server_name: z.string().min(1).max(255).default('Minecraft Server'),
  plugin_version: z.string().max(32).default('1.0.0'),
  paper_version: z.string().max(64).default('Paper 1.21'),
  capabilities: z.array(z.string()).default([]),
});

const linkSessionSchema = z.object({
  player_uuid: z.string().uuid(),
  player_name: z.string().min(1).max(64),
});

const unlinkSchema = z.object({
  player_uuid: z.string().uuid(),
});

const batchStatusSchema = z.object({
  player_uuids: z.array(z.string().uuid()).max(200),
});

/**
 * POST /v1/servers/register
 * Automatically registers or refreshes a Paper server installation identity.
 */
serverRouter.post('/register', async (req, res): Promise<void> => {
  const parseResult = registerSchema.safeParse(req.body);
  if (!parseResult.success) {
    res.status(400).json({ error: 'Invalid registration payload', details: parseResult.error.format() });
    return;
  }

  const { server_id, server_secret, server_name, plugin_version, paper_version, capabilities } = parseResult.data;

  try {
    const existing = await db.getServer(server_id);
    if (existing && existing.is_revoked) {
      res.status(403).json({ error: 'Server registration revoked' });
      return;
    }

    const secretHash = hashServerSecret(server_secret);
    const server = await db.upsertServer({
      id: server_id,
      secret_hash: secretHash,
      name: server_name,
      plugin_version,
      paper_version,
      capabilities,
    });

    res.json({
      status: 'registered',
      server_id: server.id,
      server_name: server.name,
      registered_at: server.registered_at,
    });
  } catch (err: any) {
    console.error('[Servers] Registration error:', err);
    res.status(500).json({ error: 'Internal server error during registration' });
  }
});

/**
 * POST /v1/servers/heartbeat
 * Periodic server ping and liveness update.
 */
serverRouter.post('/heartbeat', requireServerAuth, async (req: AuthenticatedServerRequest, res: Response): Promise<void> => {
  const server = req.server!;
  try {
    await db.updateHeartbeat(server.id);
    res.json({
      acknowledged: true,
      server_id: server.id,
      server_name: server.name,
      timestamp: new Date(),
    });
  } catch (err: any) {
    console.error('[Servers] Heartbeat error:', err);
    res.status(500).json({ error: 'Internal server error during heartbeat' });
  }
});

/**
 * POST /v1/servers/link-sessions
 * Creates a cryptographically random, single-use linking session associated with
 * the authenticated server installation ID and player UUID.
 */
serverRouter.post('/link-sessions', requireServerAuth, async (req: AuthenticatedServerRequest, res: Response): Promise<void> => {
  const server = req.server!;
  const parseResult = linkSessionSchema.safeParse(req.body);
  if (!parseResult.success) {
    res.status(400).json({ error: 'Invalid link session payload', details: parseResult.error.format() });
    return;
  }

  const { player_uuid, player_name } = parseResult.data;

  try {
    const sessionId = crypto.randomUUID();
    // Generate an 8-character base62 single-use code (e.g. 7kF9a2pQ)
    const code = crypto.randomBytes(6).toString('base64url').replace(/[-_]/g, 'x').slice(0, 8).toUpperCase();
    const expiresAt = new Date(Date.now() + 5 * 60 * 1000); // 5 minutes

    const session = await db.createLinkSession({
      id: sessionId,
      code,
      server_id: server.id,
      player_uuid: player_uuid.toLowerCase(),
      player_name,
      status: 'pending',
      youtube_channel_id: null,
      expires_at: expiresAt,
    });

    const webBaseUrl = process.env.MCAST_WEB_URL || 'https://mcast.fun';
    const linkUrl = `${webBaseUrl}/link/${session.code}`;

    res.json({
      session_id: session.id,
      code: session.code,
      link_url: linkUrl,
      expires_at: session.expires_at.getTime(),
      expires_in_seconds: 300,
      player_uuid: session.player_uuid,
      player_name: session.player_name,
    });
  } catch (err: any) {
    console.error('[Servers] Create link session error:', err);
    res.status(500).json({ error: 'Internal server error creating linking session' });
  }
});

/**
 * GET /v1/servers/link-sessions/:id
 * Polls the status of an active linking session.
 */
serverRouter.get('/link-sessions/:id', requireServerAuth, async (req: AuthenticatedServerRequest, res: Response): Promise<void> => {
  const server = req.server!;
  const sessionId = req.params.id;

  try {
    const session = await db.getLinkSessionById(sessionId);
    if (!session) {
      res.status(404).json({ error: 'Link session not found' });
      return;
    }

    // Cross-server isolation check
    if (session.server_id !== server.id) {
      res.status(403).json({ error: 'Forbidden: Session belongs to another server' });
      return;
    }

    // Check expiration
    if (session.status === 'pending' && new Date() > new Date(session.expires_at)) {
      session.status = 'expired';
    }

    let channelTitle: string | null = null;
    let customUrl: string | null = null;
    if (session.youtube_channel_id) {
      const acc = await db.getYouTubeAccount(session.youtube_channel_id);
      if (acc) {
        channelTitle = acc.channel_title;
        customUrl = acc.custom_url;
      }
    }

    res.json({
      session_id: session.id,
      code: session.code,
      player_uuid: session.player_uuid,
      player_name: session.player_name,
      status: session.status,
      youtube_channel_id: session.youtube_channel_id,
      channel_title: channelTitle,
      custom_url: customUrl,
      expires_at: new Date(session.expires_at).getTime(),
      consumed_at: session.consumed_at ? new Date(session.consumed_at).getTime() : null,
    });
  } catch (err: any) {
    console.error('[Servers] Poll link session error:', err);
    res.status(500).json({ error: 'Internal server error retrieving linking session' });
  }
});

/**
 * POST /v1/servers/players/unlink
 * Unlinks a player association from this Minecraft server.
 */
serverRouter.post('/players/unlink', requireServerAuth, async (req: AuthenticatedServerRequest, res: Response): Promise<void> => {
  const server = req.server!;
  const parseResult = unlinkSchema.safeParse(req.body);
  if (!parseResult.success) {
    res.status(400).json({ error: 'Invalid unlink payload', details: parseResult.error.format() });
    return;
  }

  const { player_uuid } = parseResult.data;

  try {
    const deleted = await db.unlinkPlayer(server.id, player_uuid);
    res.json({
      unlinked: deleted,
      server_id: server.id,
      player_uuid: player_uuid.toLowerCase(),
    });
  } catch (err: any) {
    console.error('[Servers] Unlink error:', err);
    res.status(500).json({ error: 'Internal server error unlinking player' });
  }
});

/**
 * POST /v1/servers/players/batch-status
 * Queries live stream statuses for a batch of online players on this server.
 * Uses centralized caching to minimize YouTube API quota consumption.
 */
serverRouter.post('/players/batch-status', requireServerAuth, async (req: AuthenticatedServerRequest, res: Response): Promise<void> => {
  const server = req.server!;
  const parseResult = batchStatusSchema.safeParse(req.body);
  if (!parseResult.success) {
    res.status(400).json({ error: 'Invalid batch status payload', details: parseResult.error.format() });
    return;
  }

  const playerUuids = parseResult.data.player_uuids.map(u => u.toLowerCase());

  try {
    const links = await db.getLinksForServer(server.id, playerUuids);
    const linkMap = new Map<string, (typeof links)[0]>();
    for (const link of links) {
      linkMap.set(link.player_uuid.toLowerCase(), link);
    }

    const statuses: Record<string, any> = {};

    for (const uuid of playerUuids) {
      const link = linkMap.get(uuid);
      if (!link) {
        statuses[uuid] = {
          linked: false,
          status: 'NOT_LINKED',
        };
        continue;
      }

      // Check live status for this YouTube channel
      const liveResult = await youtubeService.checkChannelLiveStatus(link.youtube_channel_id);
      const acc = await db.getYouTubeAccount(link.youtube_channel_id);

      statuses[uuid] = {
        linked: true,
        channel_id: link.youtube_channel_id,
        channel_title: acc ? acc.channel_title : 'YouTube Channel',
        custom_url: acc ? acc.custom_url : null,
        status: liveResult.status,
        is_live: liveResult.isLive,
        is_minecraft: liveResult.isMinecraft,
        video_id: liveResult.videoId || null,
        title: liveResult.title || null,
        stream_url: liveResult.streamUrl || null,
        checked_at: liveResult.checkedAt.toISOString(),
      };
    }

    res.json({
      server_id: server.id,
      statuses,
    });
  } catch (err: any) {
    console.error('[Servers] Batch status error:', err);
    res.status(500).json({ error: 'Internal server error checking batch status' });
  }
});
