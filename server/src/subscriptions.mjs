import { db } from './db.mjs';
import { config } from './config.mjs';
import { PLANS } from './plans.mjs';

export async function ensureFreeSubscription(userId) {
  const rows = await db().query('SELECT 1 FROM subscriptions WHERE user_id = $1 LIMIT 1', [userId]);
  if (!rows.length) {
    await db().query(
      `INSERT INTO subscriptions (user_id, plan_type, status, expires_at, payment_provider)
       VALUES ($1, 'free', 'active', 'infinity', 'manual')`,
      [userId],
    );
  }
}

export async function getEffectiveSubscription(userId) {
  const rows = await db().query(
    `SELECT * FROM subscriptions WHERE user_id = $1
     ORDER BY (status = 'active') DESC, (plan_type <> 'free') DESC, expires_at DESC
     LIMIT 1`,
    [userId],
  );
  const sub = rows[0];
  if (!sub) return null;

  const raw = sub.expires_at;
  const infinite = raw === 'infinity'
    || raw === Infinity
    || (raw instanceof Date && !Number.isFinite(raw.getTime()));
  const expiresAt = infinite ? null : new Date(raw);
  const expired = Boolean(expiresAt && expiresAt.getTime() <= Date.now());
  const status = expired && sub.status === 'active' ? 'expired' : sub.status;
  const daysLeft = expiresAt
    ? Math.max(0, Math.ceil((expiresAt.getTime() - Date.now()) / 86_400_000))
    : null;

  return {
    ...sub,
    plan: PLANS[sub.plan_type],
    infinite,
    expired,
    effectiveStatus: status,
    daysLeft,
  };
}

export function subscriptionSummary(sub) {
  const plan = sub?.plan ?? PLANS.free;
  return {
    plan_type: sub?.plan_type ?? 'free',
    plan_title: plan.title,
    status: sub?.effectiveStatus ?? 'active',
    started_at: sub?.started_at ?? null,
    expires_at: sub && !sub.infinite && sub.expires_at ? new Date(sub.expires_at).toISOString() : null,
    days_left: sub?.daysLeft ?? null,
    auto_renew: Boolean(sub?.auto_renew),
    can_play: Boolean(sub) && sub.effectiveStatus === 'active' && !sub.expired,
    limits: { max_profiles: plan.maxProfiles, max_ram_mb: plan.maxRamMb },
    renew_url: `${config.siteUrl}/billing`,
  };
}
