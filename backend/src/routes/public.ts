import { Router } from 'express';
import { db } from '../db/index.js';
import { youtubeService } from '../services/youtube.service.js';

export const publicRouter = Router();

function buildChannelUrl(channelId: string, customUrl: string | null): string {
  if (customUrl) {
    const handle = customUrl.startsWith('@') ? customUrl : `@${customUrl}`;
    return `https://www.youtube.com/${handle}`;
  }
  return `https://www.youtube.com/channel/${channelId}`;
}

/**
 * GET /v1/link-sessions/verify?code=:code
 * Public verification endpoint used by the mcast.fun linking frontend.
 * Confirms session validity and returns connected channel metadata & live status when completed.
 */
publicRouter.get('/link-sessions/verify', async (req, res): Promise<void> => {
  const code = req.query.code as string;
  if (!code) {
    res.status(400).json({ valid: false, error: 'Missing code parameter' });
    return;
  }

  try {
    const session = await db.getLinkSessionByCode(code.trim().toUpperCase());
    if (!session) {
      res.status(404).json({ valid: false, error: 'Linking code not found' });
      return;
    }

    const now = new Date();
    const isExpired = now > new Date(session.expires_at);

    // If session was completed, return connected channel and live status even if past original expires_at
    if (session.status === 'completed' && session.youtube_channel_id) {
      const server = await db.getServer(session.server_id);
      const serverName = server ? server.name : 'Minecraft Server';
      const account = await db.getYouTubeAccount(session.youtube_channel_id);
      const liveStatus = await youtubeService.checkChannelLiveStatus(session.youtube_channel_id);

      const channelTitle = account ? account.channel_title : 'YouTube Channel';
      const customUrl = account ? account.custom_url : null;
      const avatarUrl = account?.avatar_url || null;
      const channelUrl = buildChannelUrl(session.youtube_channel_id, customUrl);

      res.json({
        valid: true,
        status: 'completed',
        connected: true,
        code: session.code,
        server_name: serverName,
        player_uuid: session.player_uuid,
        player_name: session.player_name,
        channel: {
          channel_id: session.youtube_channel_id,
          channel_title: channelTitle,
          custom_url: customUrl,
          avatar_url: avatarUrl,
          channel_url: channelUrl,
        },
        live_status: {
          is_live: liveStatus.isLive,
          is_minecraft: liveStatus.isMinecraft,
          status: liveStatus.status,
          video_id: liveStatus.videoId || null,
          title: liveStatus.title || null,
          stream_url: liveStatus.streamUrl || null,
          checked_at: liveStatus.checkedAt.toISOString(),
          error: liveStatus.error || null,
        },
      });
      return;
    }

    if (isExpired) {
      res.json({
        valid: false,
        status: 'expired',
        error: 'This linking session has expired. Run /link youtube in Minecraft for a new link.',
      });
      return;
    }

    if (session.status !== 'pending') {
      res.json({
        valid: false,
        status: session.status,
        error: `This session has already been ${session.status}.`,
      });
      return;
    }

    // Retrieve server metadata for pending session
    const server = await db.getServer(session.server_id);
    const serverName = server ? server.name : 'Minecraft Server';
    const secondsRemaining = Math.max(0, Math.floor((new Date(session.expires_at).getTime() - now.getTime()) / 1000));

    res.json({
      valid: true,
      code: session.code,
      server_name: serverName,
      player_uuid: session.player_uuid,
      player_name: session.player_name,
      status: session.status,
      connected: false,
      expires_at: new Date(session.expires_at).getTime(),
      seconds_remaining: secondsRemaining,
    });
  } catch (err: any) {
    console.error('[Public] Error verifying link session:', err);
    res.status(500).json({ valid: false, error: 'Internal server error verifying session' });
  }
});

/**
 * GET /v1/link-sessions/status?code=:code
 * Dedicated status endpoint for polling and checking a session's current status and live state.
 */
publicRouter.get('/link-sessions/status', async (req, res): Promise<void> => {
  const code = req.query.code as string;
  if (!code) {
    res.status(400).json({ error: 'Missing code parameter' });
    return;
  }

  try {
    const session = await db.getLinkSessionByCode(code.trim().toUpperCase());
    if (!session) {
      res.status(404).json({ error: 'Session not found' });
      return;
    }

    const server = await db.getServer(session.server_id);
    const serverName = server ? server.name : 'Minecraft Server';

    let channelData = null;
    let liveStatusData = null;

    if (session.youtube_channel_id) {
      const account = await db.getYouTubeAccount(session.youtube_channel_id);
      const liveStatus = await youtubeService.checkChannelLiveStatus(session.youtube_channel_id);

      channelData = {
        channel_id: session.youtube_channel_id,
        channel_title: account ? account.channel_title : 'YouTube Channel',
        custom_url: account?.custom_url || null,
        avatar_url: account?.avatar_url || null,
        channel_url: buildChannelUrl(session.youtube_channel_id, account?.custom_url || null),
      };

      liveStatusData = {
        is_live: liveStatus.isLive,
        is_minecraft: liveStatus.isMinecraft,
        status: liveStatus.status,
        video_id: liveStatus.videoId || null,
        title: liveStatus.title || null,
        stream_url: liveStatus.streamUrl || null,
        checked_at: liveStatus.checkedAt.toISOString(),
        error: liveStatus.error || null,
      };
    }

    res.json({
      code: session.code,
      status: session.status,
      connected: session.status === 'completed' && !!session.youtube_channel_id,
      server_name: serverName,
      player_uuid: session.player_uuid,
      player_name: session.player_name,
      channel: channelData,
      live_status: liveStatusData,
      expires_at: new Date(session.expires_at).getTime(),
    });
  } catch (err: any) {
    console.error('[Public] Error fetching session status:', err);
    res.status(500).json({ error: 'Internal server error' });
  }
});

/**
 * GET /v1/channels/:channelId/status
 * Public endpoint to query channel live status and metadata without exposing tokens.
 */
publicRouter.get('/channels/:channelId/status', async (req, res): Promise<void> => {
  const { channelId } = req.params;
  if (!channelId) {
    res.status(400).json({ error: 'Missing channelId' });
    return;
  }

  try {
    const account = await db.getYouTubeAccount(channelId);
    const liveStatus = await youtubeService.checkChannelLiveStatus(channelId);

    const customUrl = account?.custom_url || null;

    res.json({
      channel_id: channelId,
      channel_title: account ? account.channel_title : 'YouTube Channel',
      custom_url: customUrl,
      avatar_url: account?.avatar_url || null,
      channel_url: buildChannelUrl(channelId, customUrl),
      status: liveStatus.status,
      is_live: liveStatus.isLive,
      is_minecraft: liveStatus.isMinecraft,
      video_id: liveStatus.videoId || null,
      title: liveStatus.title || null,
      stream_url: liveStatus.streamUrl || null,
      checked_at: liveStatus.checkedAt.toISOString(),
      error: liveStatus.error || null,
    });
  } catch (err: any) {
    console.error('[Public] Error querying channel status:', err);
    res.status(500).json({ error: 'Internal server error querying channel' });
  }
});
