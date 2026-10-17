import { Router } from 'express';
import { z } from 'zod';
import { config } from '../config.mjs';
import { db } from '../db.mjs';
import { kv } from '../kv.mjs';
import { ah, parse } from '../http.mjs';
import { notFound } from '../errors.mjs';
import { normalizeEmail } from '../util.mjs';
import { PLAN_ORDER } from '../plans.mjs';

const router = Router();

function devGuard(req, res, next) {
  if (!config.devMode || !config.devPeekToken) return next(notFound());
  if (req.get('x-dev-token') !== config.devPeekToken) return next(notFound());
  next();
}

router.use(devGuard);

router.get('/ping', ah(async (req, res) => {
  res.json({ ok: true, node_env: config.nodeEnv, time: new Date().toISOString() });
}));

router.post('/peek-otp', ah(async (req, res) => {
  const { email } = parse(z.object({ email: z.string().email() }), req.body);
  const code = await kv().get(`dev:otp:${normalizeEmail(email)}`);
  if (!code) throw notFound('dev_no_otp', 'Активный код не найден');
  res.json({ email: normalizeEmail(email), code });
}));

router.post('/activate-subscription', ah(async (req, res) => {
  const body = parse(
    z.object({
      email: z.string().email(),
      plan_type: z.enum(['free', 'premium', 'legendary']).default('legendary'),
      days: z.number().int().min(1).max(3650).default(30),
      expired: z.boolean().default(false),
    }),
    req.body,
  );
  const email = normalizeEmail(body.email);
  const users = await db().query('SELECT id FROM users WHERE lower(email) = $1', [email]);
  const user = users[0];
  if (!user) throw notFound('user_not_found', 'Пользователь не найден');

  await db().tx(async (tx) => {
    await tx.query(
      `UPDATE subscriptions SET status = 'expired'
       WHERE user_id = $1 AND status IN ('active', 'pending')`,
      [user.id],
    );
    if (body.expired) {
      await tx.query(
        `INSERT INTO subscriptions (user_id, plan_type, status, started_at, expires_at, payment_provider)
         VALUES ($1, $2, 'expired', now() - interval '31 days', now() - interval '1 day', 'manual')`,
        [user.id, body.plan_type],
      );
    } else {
      await tx.query(
        `INSERT INTO subscriptions (user_id, plan_type, status, expires_at, payment_provider)
         VALUES ($1, $2, 'active', now() + ($3::int * interval '1 day'), 'manual')`,
        [user.id, body.plan_type, body.days],
      );
    }
  });

  const rows = await db().query(
    `SELECT plan_type, status, expires_at FROM subscriptions
     WHERE user_id = $1 ORDER BY created_at DESC LIMIT 1`,
    [user.id],
  );
  res.json({ ok: true, subscription: rows[0] });
}));

router.post('/ban-hwid', ah(async (req, res) => {
  const body = parse(
    z.object({ hwid: z.string().min(4).max(128), reason: z.string().min(1).max(200).default('dev ban') }),
    req.body,
  );
  await db().query(
    `INSERT INTO hwid_bans (hwid, reason) VALUES ($1, $2)
     ON CONFLICT (hwid) DO NOTHING`,
    [body.hwid, body.reason],
  );
  res.json({ ok: true });
}));

router.get('/plans', (req, res) => {
  res.json({ plans: PLAN_ORDER });
});

export default router;
