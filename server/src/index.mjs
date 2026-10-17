import express from 'express';
import cors from 'cors';
import helmet from 'helmet';
import { config } from './config.mjs';
import { initDb, db } from './db.mjs';
import { initKv, kv } from './kv.mjs';
import { runMigrations } from './migrate.mjs';
import { launcherHmac, requireLauncherSignature } from './middleware/hmac.mjs';
import { requireAuth } from './middleware/auth.mjs';
import { launcherWriteGuard } from './middleware/guard.mjs';
import { errorHandler, notFoundHandler } from './middleware/errorHandler.mjs';
import authRouter from './routes/auth.mjs';
import devRouter from './routes/dev.mjs';
import websiteRouter from './routes/website/index.mjs';
import launcherRouter from './routes/launcher/index.mjs';

async function main() {
  await initDb();
  initKv();
  console.log(`[db] connected: ${db().kind}`);
  console.log(`[kv] connected: ${kv().kind}`);

  if (config.autoMigrate) {
    const applied = await runMigrations();
    if (applied.length) console.log(`[db] migrations applied: ${applied.join(', ')}`);
  }

  const app = express();
  app.disable('x-powered-by');
  app.set('trust proxy', true);

  app.use(helmet());
  app.use(cors({
    origin(origin, callback) {
      if (!origin || config.websiteOrigins.includes(origin) || config.devMode) {
        return callback(null, true);
      }
      return callback(new Error('origin not allowed'));
    },
  }));
  app.use(express.json({
    limit: '256kb',
    verify(req, res, buf) {
      req.rawBody = buf;
    },
  }));

  app.use(launcherHmac);

  app.get('/health', (req, res) => {
    res.json({ ok: true, env: config.nodeEnv, db: db().kind, kv: kv().kind });
  });

  app.use('/auth', authRouter);
  app.use('/api/website', requireAuth('website'), websiteRouter);
  app.use('/api/launcher', requireAuth('launcher'), requireLauncherSignature, launcherWriteGuard, launcherRouter);
  app.use('/dev', devRouter);

  app.use(notFoundHandler);
  app.use(errorHandler);

  const server = app.listen(config.port, () => {
    console.log(`[server] listening on http://localhost:${config.port} (${config.nodeEnv})`);
  });

  const shutdown = async (signal) => {
    console.log(`[server] ${signal} received, shutting down`);
    server.close(async () => {
      try {
        await kv().close();
        await db().close();
      } finally {
        process.exit(0);
      }
    });
    setTimeout(() => process.exit(1), 5000).unref();
  };
  process.on('SIGINT', () => shutdown('SIGINT'));
  process.on('SIGTERM', () => shutdown('SIGTERM'));
}

main().catch((err) => {
  console.error('[fatal]', err);
  process.exit(1);
});
