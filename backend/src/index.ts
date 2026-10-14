import express from 'express';
import helmet from 'helmet';
import cors from 'cors';
import rateLimit from 'express-rate-limit';
import dotenv from 'dotenv';
import { db } from './db/index.js';
import { serverRouter } from './routes/servers.js';
import { oauthRouter } from './routes/oauth.js';
import { publicRouter } from './routes/public.js';

dotenv.config();

const app = express();
const PORT = process.env.PORT || 4000;

// Trust reverse proxy headers on Render / Cloudflare
app.set('trust proxy', 1);

// Security and CORS middleware
app.use(helmet());
app.use(cors({ origin: true, credentials: true }));

app.use(express.json({ limit: '1mb' }));
app.use(express.urlencoded({ extended: true, limit: '1mb' }));

// Rate Limiting to prevent registration and brute-force abuse
const registrationLimiter = rateLimit({
  windowMs: 15 * 60 * 1000, // 15 minutes
  max: 30, // 30 registrations per IP
  standardHeaders: true,
  legacyHeaders: false,
  message: { error: 'Too many server registration requests. Please try again later.' },
});

const oauthLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 60,
  standardHeaders: true,
  legacyHeaders: false,
  message: { error: 'Too many authentication attempts. Please try again later.' },
});

// Health Check with Database Status
app.get('/health', async (req, res) => {
  const dbStatus = db.getStatus();
  const dbPing = await db.ping();
  res.json({
    status: 'healthy',
    service: 'mcast-backend',
    version: '1.0.0',
    timestamp: new Date().toISOString(),
    database: {
      ...dbStatus,
      ...dbPing,
    },
  });
});

// Mount API routes with security rate limiters
app.use('/v1/servers/register', registrationLimiter);
app.use('/v1/servers', serverRouter);
app.use('/v1/oauth', oauthLimiter, oauthRouter);
app.use('/v1', publicRouter);

// 404 Fallback
app.use((req, res) => {
  res.status(404).json({ error: 'Endpoint not found' });
});

// Global Error Handler
app.use((err: any, req: express.Request, res: express.Response, next: express.NextFunction) => {
  console.error('[Error] Unhandled exception:', err);
  res.status(500).json({ error: 'Internal server error' });
});

// Initialize database asynchronously and start server immediately
if (process.env.NODE_ENV !== 'test') {
  app.listen(PORT, () => {
    console.log(`[MCast Backend] Listening on port ${PORT}`);
    console.log(`[MCast Backend] OAuth Redirect URI: ${process.env.GOOGLE_REDIRECT_URI || 'https://api.mcast.fun/v1/oauth/callback'}`);

    // Asynchronously connect to DB without blocking HTTP server
    db.init().catch(err => {
      console.error('[DB] Background initialization error:', err);
    });
  });
}

export default app;
