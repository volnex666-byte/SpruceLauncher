import { Router } from 'express';
import { z } from 'zod';
import { db } from '../../db.mjs';
import { ah, parse } from '../../http.mjs';
import { badRequest } from '../../errors.mjs';
import { getEffectiveSubscription, subscriptionSummary } from '../../subscriptions.mjs';
import { logSecurityEvent } from '../../middleware/guard.mjs';

const router = Router();

router.get('/me', ah(async (req, res) => {
  const rows = await db().query('SELECT * FROM users WHERE id = $1', [req.user.id]);
  const user = rows[0];
  const counts = await db().query(
    'SELECT count(*)::int AS profiles FROM profiles WHERE user_id = $1',
    [req.user.id],
  );
  const sub = await getEffectiveSubscription(user.id);
  res.json({
    user: {
      id: Number(user.id),
      email: user.email,
      created_at: user.created_at,
      last_login_at: user.last_login_at,
      hwid_bound: Boolean(user.hwid),
      hwid_masked: user.hwid ? `${String(user.hwid).slice(0, 8)}…` : null,
    },
    subscription: subscriptionSummary(sub),
    profiles_count: counts[0].profiles,
  });
}));

router.patch('/me', ah(async (req, res) => {
  const body = parse(z.object({ hwid: z.string().min(4).max(128).nullable() }), req.body);
  await db().query('UPDATE users SET hwid = $1 WHERE id = $2', [body.hwid, req.user.id]);
  await logSecurityEvent({
    userId: req.user.id,
    ip: req.user.ip,
    eventType: body.hwid ? 'hwid_rebound' : 'hwid_unbound',
    details: { hwid: body.hwid ? String(body.hwid).slice(0, 8) : null },
  });
  res.json({ ok: true, hwid_bound: Boolean(body.hwid) });
}));

router.get('/me/sessions', ah(async (req, res) => {
  const rows = await db().query(
    `SELECT id, client_type, ip, user_agent, created_at, expires_at
     FROM sessions
     WHERE user_id = $1 AND revoked_at IS NULL AND expires_at > now()
     ORDER BY created_at DESC
     LIMIT 50`,
    [req.user.id],
  );
  res.json({ sessions: rows });
}));

router.delete('/me/sessions/:id', ah(async (req, res) => {
  const id = Number.parseInt(req.params.id, 10);
  if (!Number.isFinite(id)) throw badRequest('bad_id', 'Некорректный id сессии');
  await db().query(
    'UPDATE sessions SET revoked_at = now() WHERE id = $1 AND user_id = $2 AND revoked_at IS NULL',
    [id, req.user.id],
  );
  res.json({ ok: true });
}));

router.get('/me/login-logs', ah(async (req, res) => {
  const rows = await db().query(
    `SELECT ip, device, success, reason, created_at
     FROM login_logs WHERE user_id = $1
     ORDER BY created_at DESC LIMIT 50`,
    [req.user.id],
  );
  res.json({ logs: rows });
}));

export default router;
