import { Router } from 'express';
import { db } from '../../db.mjs';
import { ah } from '../../http.mjs';
import { getEffectiveSubscription, subscriptionSummary } from '../../subscriptions.mjs';

const router = Router();

router.get('/me', ah(async (req, res) => {
  const rows = await db().query(
    'SELECT id, email, created_at, last_login_at, hwid FROM users WHERE id = $1',
    [req.user.id],
  );
  const user = rows[0];
  const sub = await getEffectiveSubscription(user.id);
  const counts = await db().query(
    'SELECT count(*)::int AS profiles FROM profiles WHERE user_id = $1',
    [user.id],
  );
  res.json({
    user: {
      id: Number(user.id),
      email: user.email,
      created_at: user.created_at,
      last_login_at: user.last_login_at,
      hwid_bound: Boolean(user.hwid),
      hwid_trusted: req.user.hwidTrusted,
    },
    subscription: subscriptionSummary(sub),
    profiles_count: counts[0].profiles,
  });
}));

export default router;
