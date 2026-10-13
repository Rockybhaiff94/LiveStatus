import { Router } from 'express';
import crypto from 'node:crypto';
import { db } from '../db/index.js';
import { signOAuthState, verifyOAuthState, encryptToken } from '../crypto/encryption.js';
import { youtubeService } from '../services/youtube.service.js';

export const oauthRouter = Router();

/**
 * GET /v1/oauth/authorize
 * Initiates the Google OAuth authorization flow for a specific single-use link session code.
 */
oauthRouter.get('/authorize', async (req, res): Promise<void> => {
  const sessionCode = req.query.code as string;
  const webBaseUrl = process.env.MCAST_WEB_URL || 'https://mcast.fun';

  if (!sessionCode) {
    res.redirect(`${webBaseUrl}/link/error?reason=missing_code`);
    return;
  }

  try {
    const session = await db.getLinkSessionByCode(sessionCode);
    if (!session) {
      res.redirect(`${webBaseUrl}/link/error?reason=invalid_session`);
      return;
    }

    if (session.status !== 'pending') {
      res.redirect(`${webBaseUrl}/link/error?reason=session_${session.status}`);
      return;
    }

    if (new Date() > new Date(session.expires_at)) {
      res.redirect(`${webBaseUrl}/link/error?reason=session_expired`);
      return;
    }

    // Sign a tamper-proof OAuth state token bound to this session ID
    const statePayload = {
      session_id: session.id,
      session_code: session.code,
      nonce: crypto.randomBytes(16).toString('hex'),
      expires_at: Date.now() + 10 * 60 * 1000, // 10 minutes state validity
    };
    const signedState = signOAuthState(statePayload);

    const clientId = process.env.GOOGLE_CLIENT_ID || '';
    const redirectUri = process.env.GOOGLE_REDIRECT_URI || 'https://api.mcast.fun/v1/oauth/callback';
    const scope = 'https://www.googleapis.com/auth/youtube.readonly';

    const googleAuthUrl = new URL('https://accounts.google.com/o/oauth2/v2/auth');
    googleAuthUrl.searchParams.set('client_id', clientId);
    googleAuthUrl.searchParams.set('redirect_uri', redirectUri);
    googleAuthUrl.searchParams.set('response_type', 'code');
    googleAuthUrl.searchParams.set('scope', scope);
    googleAuthUrl.searchParams.set('access_type', 'offline');
    googleAuthUrl.searchParams.set('prompt', 'consent');
    googleAuthUrl.searchParams.set('state', signedState);

    res.redirect(googleAuthUrl.toString());
  } catch (err: any) {
    console.error('[OAuth] Authorize initiation error:', err);
    res.redirect(`${webBaseUrl}/link/error?reason=internal_error`);
  }
});

/**
 * GET /v1/oauth/callback
 * Handles the Google OAuth callback redirect.
 * Exchanges authorization code, identifies YouTube channel, securely encrypts refresh token at rest,
 * and completes player linking association.
 */
oauthRouter.get('/callback', async (req, res): Promise<void> => {
  const webBaseUrl = process.env.MCAST_WEB_URL || 'https://mcast.fun';
  const { code, state, error } = req.query;

  if (error) {
    console.warn('[OAuth] Google OAuth callback returned error:', error);
    res.redirect(`${webBaseUrl}/link/error?reason=consent_denied&details=${encodeURIComponent(String(error))}`);
    return;
  }

  if (!code || !state) {
    res.redirect(`${webBaseUrl}/link/error?reason=missing_callback_params`);
    return;
  }

  // 1. Verify and decode state token signature
  const stateData = verifyOAuthState<{ session_id: string; session_code: string; expires_at: number }>(String(state));
  if (!stateData) {
    console.warn('[OAuth] Rejected callback: Invalid or forged state token signature');
    res.redirect(`${webBaseUrl}/link/error?reason=invalid_state`);
    return;
  }

  if (Date.now() > stateData.expires_at) {
    res.redirect(`${webBaseUrl}/link/error?reason=state_expired`);
    return;
  }

  try {
    // 2. Retrieve session from database
    const session = await db.getLinkSessionById(stateData.session_id);
    if (!session) {
      res.redirect(`${webBaseUrl}/link/error?reason=session_not_found`);
      return;
    }

    if (session.status !== 'pending') {
      res.redirect(`${webBaseUrl}/link/error?reason=session_already_${session.status}`);
      return;
    }

    if (new Date() > new Date(session.expires_at)) {
      res.redirect(`${webBaseUrl}/link/error?reason=session_expired`);
      return;
    }

    // 3. Exchange code for access & refresh tokens
    const tokens = await youtubeService.exchangeCode(String(code));
    if (!tokens.refreshToken) {
      console.warn('[OAuth] Warning: Google did not return a refresh_token. Re-prompt consent may be needed.');
    }

    // 4. Retrieve creator's YouTube channel profile (1 quota unit)
    const channel = await youtubeService.getChannelProfile(tokens.accessToken);

    // 5. Encrypt refresh token at rest using AES-256-GCM
    const encrypted = encryptToken(tokens.refreshToken || tokens.accessToken);

    // 6. Persist YouTube account credentials in DB
    await db.upsertYouTubeAccount({
      channel_id: channel.channelId,
      channel_title: channel.title,
      custom_url: channel.customUrl,
      encrypted_refresh_token: encrypted.ciphertext,
      token_iv: encrypted.iv,
      token_auth_tag: encrypted.authTag,
      token_status: 'active',
      last_refreshed_at: new Date(),
    });

    // 7. Associate Minecraft player with this YouTube account
    await db.linkPlayer({
      server_id: session.server_id,
      player_uuid: session.player_uuid,
      player_name: session.player_name,
      youtube_channel_id: channel.channelId,
      is_authorized: true,
    });

    // 8. Mark single-use session as completed
    await db.completeLinkSession(session.id, channel.channelId);

    // 9. Redirect player to success page on mcast.fun
    res.redirect(`${webBaseUrl}/link/${session.code}/success?channel=${encodeURIComponent(channel.title)}&player=${encodeURIComponent(session.player_name)}`);
  } catch (err: any) {
    console.error('[OAuth] Token exchange / linking error:', err);
    res.redirect(`${webBaseUrl}/link/error?reason=exchange_failed&details=${encodeURIComponent(err.message || 'unknown')}`);
  }
});
