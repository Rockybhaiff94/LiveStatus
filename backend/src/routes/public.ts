import { Router } from 'express';
import { db } from '../db/index.js';

export const publicRouter = Router();

/**
 * GET /v1/link-sessions/verify?code=:code
 * Public verification endpoint used by the mcast.fun linking frontend.
 * Confirms session validity and displays server and player context safely.
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

    // Retrieve server metadata
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
      expires_at: new Date(session.expires_at).getTime(),
      seconds_remaining: secondsRemaining,
    });
  } catch (err: any) {
    console.error('[Public] Error verifying link session:', err);
    res.status(500).json({ valid: false, error: 'Internal server error verifying session' });
  }
});
