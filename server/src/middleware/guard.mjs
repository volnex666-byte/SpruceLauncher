import { config } from '../config.mjs';
import { db } from '../db.mjs';
import { forbidden } from '../errors.mjs';
import { clientIp, sha256hex } from '../util.mjs';

export async function logSecurityEvent({ userId = null, hwidHash = null, ip = null, eventType, details = {} }) {
  try {
    await db().query(
      `INSERT INTO security_events (user_id, hwid, ip, event_type, details)
       VALUES ($1, $2, $3, $4, $5::jsonb)`,
      [userId, hwidHash, ip, eventType, JSON.stringify(details)],
    );
  } catch {
    // security logging must never break the request flow
  }
}

export function websiteOriginGuard(req, res, next) {
  const origin = req.get('origin');
  if (origin && !config.websiteOrigins.includes(origin)) {
    return next(forbidden('bad_origin', 'Неизвестный источник запроса'));
  }
  next();
}

async function maybeBanHwid(user, req) {
  if (!config.autoBanOnScopeViolation || !user?.id) return;
  try {
    const rows = await db().query('SELECT hwid FROM users WHERE id = $1', [user.id]);
    const hwid = rows[0]?.hwid;
    if (!hwid) return;
    await db().query(
      `INSERT INTO hwid_bans (hwid, user_id, reason) VALUES ($1, $2, 'launcher_write_attempt')
       ON CONFLICT (hwid) DO NOTHING`,
      [hwid, user.id],
    );
    await db().query('UPDATE users SET is_banned = true WHERE id = $1', [user.id]);
  } catch {
    // ban is best-effort; the 403 below is authoritative
  }
  void req;
}

export async function launcherWriteGuard(req, res, next) {
  if (req.method === 'GET' || req.method === 'HEAD' || req.method === 'OPTIONS') return next();
  const user = req.user;
  if (!user || user.clientType !== 'launcher') return next();

  console.warn(`[security] launcher write attempt: ${req.method} ${req.originalUrl} user=${user.id}`);
  await logSecurityEvent({
    userId: user.id,
    hwidHash: user.hwidHash ?? null,
    ip: clientIp(req),
    eventType: 'launcher_write_attempt',
    details: { method: req.method, path: req.originalUrl, body_sha256: sha256hex(JSON.stringify(req.body ?? {})) },
  });
  await maybeBanHwid(user, req);
  next(forbidden('read_only_client', 'Лаунчер не может изменять данные'));
}
