import { Request, Response, NextFunction } from 'express';
import { db, ServerRecord } from '../db/index.js';
import { verifyServerSecret } from '../crypto/encryption.js';

export interface AuthenticatedServerRequest extends Request {
  server?: ServerRecord;
}

/**
 * Middleware ensuring incoming requests from Paper Minecraft servers
 * provide valid installation credentials (X-MCast-Server-Id and Bearer secret).
 */
export async function requireServerAuth(
  req: AuthenticatedServerRequest,
  res: Response,
  next: NextFunction
): Promise<void> {
  const serverId = req.header('X-MCast-Server-Id');
  const authHeader = req.header('Authorization');

  if (!serverId || !authHeader) {
    res.status(401).json({
      error: 'Unauthorized: Missing X-MCast-Server-Id or Authorization header',
    });
    return;
  }

  const parts = authHeader.split(' ');
  if (parts.length !== 2 || parts[0].toLowerCase() !== 'bearer') {
    res.status(401).json({
      error: 'Unauthorized: Invalid Authorization header format. Expected "Bearer <server_secret>"',
    });
    return;
  }

  const providedSecret = parts[1];

  try {
    const server = await db.getServer(serverId);
    if (!server) {
      res.status(401).json({ error: 'Unauthorized: Unrecognized server installation ID' });
      return;
    }

    if (server.is_revoked) {
      res.status(403).json({ error: 'Forbidden: Server registration has been revoked' });
      return;
    }

    const isValid = verifyServerSecret(providedSecret, server.secret_hash);
    if (!isValid) {
      res.status(401).json({ error: 'Unauthorized: Invalid server authentication secret' });
      return;
    }

    // Attach server record to request
    req.server = server;
    next();
  } catch (err: any) {
    console.error('[Auth] Error verifying server authentication:', err);
    res.status(500).json({ error: 'Internal server error during authentication' });
  }
}
