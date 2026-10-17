import { Router } from 'express';
import { z } from 'zod';
import { db } from '../../db.mjs';
import { ah, parse } from '../../http.mjs';
import { badRequest, notFound } from '../../errors.mjs';
import { getEffectiveSubscription, subscriptionSummary } from '../../subscriptions.mjs';
import { requireScope } from '../../middleware/auth.mjs';

const router = Router();

router.get('/', ah(async (req, res) => {
  const sub = await getEffectiveSubscription(req.user.id);
  const history = await db().query(
    `SELECT id, plan_type, status, started_at, expires_at, auto_renew, payment_provider, created_at
     FROM subscriptions WHERE user_id = $1
     ORDER BY created_at DESC LIMIT 30`,
    [req.user.id],
  );
  const payments = await db().query(
    `SELECT id, amount_cents, currency, status, provider, created_at
     FROM payments WHERE user_id = $1
     ORDER BY created_at DESC LIMIT 50`,
    [req.user.id],
  );
  res.json({
    subscription: subscriptionSummary(sub),
    history,
    payments,
  });
}));

router.post('/cancel', requireScope('website:write'), ah(async (req, res) => {
  const body = parse(
    z.object({ subscription_id: z.string().uuid().optional() }).default({}),
    req.body ?? {},
  );
  const rows = body.subscription_id
    ? await db().query(
        `SELECT * FROM subscriptions
         WHERE id = $1 AND user_id = $2 AND status IN ('active', 'pending')`,
        [body.subscription_id, req.user.id],
      )
    : await db().query(
        `SELECT * FROM subscriptions
         WHERE user_id = $1 AND status IN ('active', 'pending') AND plan_type <> 'free'
         ORDER BY created_at DESC LIMIT 1`,
        [req.user.id],
      );

  const sub = rows[0];
  if (!sub) throw notFound('nothing_to_cancel', 'Нет активной платной подписки');
  if (sub.plan_type === 'free') throw badRequest('cannot_cancel_free', 'Платный план не найден');

  await db().query(
    `UPDATE subscriptions SET status = 'cancelled', auto_renew = false WHERE id = $1`,
    [sub.id],
  );
  const updated = await getEffectiveSubscription(req.user.id);
  res.json({ ok: true, subscription: subscriptionSummary(updated), cancelled_until: sub.expires_at });
}));

export default router;
