import { Router } from 'express';
import { z } from 'zod';
import { config } from '../config.mjs';
import { db } from '../db.mjs';
import { kv } from '../kv.mjs';
import { ah, parse } from '../http.mjs';
import { badRequest, forbidden, unauthorized } from '../errors.mjs';
import { checkEmailDomain, clientIp, normalizeEmail, sha256hex } from '../util.mjs';
import { createOtp, verifyOtp } from '../otp.mjs';
import { sendOtpEmail } from '../mailer.mjs';
import { signAccessToken, newRefreshToken, hashRefreshToken } from '../jwt.mjs';
import { ensureFreeSubscription, getEffectiveSubscription, subscriptionSummary } from '../subscriptions.mjs';
import { rateLimit, byIp, byBodyEmail } from '../middleware/rateLimit.mjs';
import { logSecurityEvent } from '../middleware/guard.mjs';

const router = Router();

const emailSchema = z.string().trim().toLowerCase().email().max(254);
const requestSchema = z.object({ email: emailSchema });
const verifySchema = z.object({
  email: emailSchema,
  code: z.string().regex(/^\d{6}$/, 'Код состоит из 6 цифр'),
  client_type: z.enum(['website', 'launcher']),
  hwid: z.string().min(4).max(128).optional(),
});
const refreshSchema = z.object({
  refresh_token: z.string().min(20),
  client_type: z.enum(['website', 'launcher']),
});
const logoutSchema = z.object({ refresh_token: z.string().min(20) });

function userPublic(user) {
  return {
    id: Number(user.id),
    email: user.email,
    created_at: user.created_at,
    hwid_bound: Boolean(user.hwid),
    hwid_masked: user.hwid ? `${String(user.hwid).slice(0, 8)}…` : null,
  };
}

async function logLogin(userId, email, req, success, reason) {
  try {
    await db().query(
      `INSERT INTO login_logs (user_id, email, ip, device, hwid, success, reason)
       VALUES ($1, $2, $3, $4, $5, $6, $7)`,
      [
        userId,
        email,
        clientIp(req),
        req.get('user-agent') ?? null,
        req.body?.hwid ?? null,
        success,
        reason,
      ],
    );
  } catch {
    // best-effort login journal
  }
}

async function createSession(userId, clientType, refreshHash, req, hwidRaw) {
  const expires = new Date(Date.now() + config.refreshTtlSec * 1000);
  const rows = await db().query(
    `INSERT INTO sessions (user_id, refresh_token_hash, client_type, hwid, ip, user_agent, expires_at)
     VALUES ($1, $2, $3, $4, $5, $6, $7)
     RETURNING id`,
    [userId, refreshHash, clientType, hwidRaw ?? null, clientIp(req), req.get('user-agent') ?? null, expires],
  );
  return rows[0].id;
}

router.post(
  '/request-code',
  rateLimit({ name: 'otp-email', limit: 5, windowSec: 3600, keyFn: byBodyEmail }),
  rateLimit({ name: 'otp-ip', limit: 10, windowSec: 3600, keyFn: byIp }),
  ah(async (req, res) => {
    const { email } = parse(requestSchema, req.body);

    if (!(await checkEmailDomain(email))) {
      throw badRequest('email_domain', 'Используйте другой адрес электронной почты');
    }

    const existing = await db().query('SELECT * FROM users WHERE lower(email) = $1', [email]);
    let user = existing[0];
    let isNew = false;
    if (!user) {
      const inserted = await db().query(
        `INSERT INTO users (email) VALUES ($1)
         ON CONFLICT (lower(email)) DO NOTHING
         RETURNING *`,
        [email],
      );
      if (inserted[0]) {
        user = inserted[0];
        isNew = true;
      } else {
        const again = await db().query('SELECT * FROM users WHERE lower(email) = $1', [email]);
        user = again[0];
      }
      await ensureFreeSubscription(user.id);
    }

    let devCode = null;
    if (user && !user.is_banned) {
      const code = await createOtp(user.id);
      if (isNew) {
        await kv().set(`newuser:${user.id}`, '1', 30 * 60);
      }
      if (config.devMode && config.devPeekToken) {
        await kv().set(`dev:otp:${email}`, code, config.otpTtlSec);
      }
      await sendOtpEmail(email, code);
      if (config.devMode && !config.smtp.host) {
        devCode = code;
      }
    }

    res.json({
      ok: true,
      expires_in: config.otpTtlSec,
      ...(devCode ? { dev_code: devCode } : {}),
    });
  }),
);

router.post(
  '/verify-code',
  rateLimit({ name: 'verify-ip', limit: 30, windowSec: 600, keyFn: byIp }),
  ah(async (req, res) => {
    const body = parse(verifySchema, req.body);
    const email = body.email;

    const rows = await db().query('SELECT * FROM users WHERE lower(email) = $1', [email]);
    const user = rows[0];
    if (!user) {
      await logLogin(null, email, req, false, 'user_not_found');
      throw unauthorized('code_invalid', 'Неверный или просроченный код');
    }
    if (user.is_banned) {
      await logLogin(user.id, email, req, false, 'banned');
      throw forbidden('user_banned', 'Аккаунт заблокирован');
    }

    const verdict = await verifyOtp(user.id, body.code);
    if (!verdict.ok) {
      await logLogin(user.id, email, req, false, verdict.reason);
      throw unauthorized('code_invalid', 'Неверный или просроченный код');
    }

    let hwidRaw = null;
    let hwidTrusted = true;
    if (body.client_type === 'launcher') {
      if (!body.hwid) throw badRequest('hwid_required', 'Не передан идентификатор устройства');
      hwidRaw = body.hwid;
      const banned = await db().query('SELECT 1 FROM hwid_bans WHERE hwid = $1', [hwidRaw]);
      if (banned.length) {
        await logSecurityEvent({
          userId: user.id,
          eventType: 'banned_hwid_login',
          ip: clientIp(req),
          details: { hwid: hwidRaw },
        });
        throw forbidden('hwid_banned', 'Устройство заблокировано');
      }
      if (!user.hwid) {
        await db().query('UPDATE users SET hwid = $1 WHERE id = $2', [hwidRaw, user.id]);
        hwidTrusted = true;
      } else if (user.hwid === hwidRaw) {
        hwidTrusted = true;
      } else {
        hwidTrusted = false;
        await logSecurityEvent({
          userId: user.id,
          hwidHash: sha256hex(hwidRaw),
          eventType: 'hwid_mismatch',
          ip: clientIp(req),
          details: { bound: String(user.hwid).slice(0, 8) },
        });
      }
      await db().query('UPDATE users SET last_login_at = now() WHERE id = $1', [user.id]);
    } else {
      await db().query('UPDATE users SET last_login_at = now() WHERE id = $1', [user.id]);
    }

    const { token: refreshToken, hash: refreshHash } = newRefreshToken();
    await createSession(user.id, body.client_type, refreshHash, req, hwidRaw);
    const hwidHash = hwidRaw ? sha256hex(hwidRaw) : null;
    const accessToken = signAccessToken({
      userId: user.id,
      clientType: body.client_type,
      hwidHash,
      hwidTrusted,
    });

    await logLogin(user.id, email, req, true, null);

    const isNewUser = (await kv().get(`newuser:${user.id}`)) === '1';
    if (isNewUser) await kv().del(`newuser:${user.id}`);

    res.json({
      access_token: accessToken,
      refresh_token: refreshToken,
      token_type: 'Bearer',
      expires_in: config.accessTtlSec,
      user: userPublic(user),
      client_type: body.client_type,
      hwid_trusted: hwidTrusted,
      is_new_user: isNewUser,
      subscription: subscriptionSummary(await getEffectiveSubscription(user.id)),
    });
  }),
);

router.post(
  '/refresh',
  rateLimit({ name: 'refresh-ip', limit: 60, windowSec: 600, keyFn: byIp }),
  ah(async (req, res) => {
    const body = parse(refreshSchema, req.body);
    const tokenHash = hashRefreshToken(body.refresh_token);

    const rows = await db().query('SELECT * FROM sessions WHERE refresh_token_hash = $1 LIMIT 1', [tokenHash]);
    const session = rows[0];
    if (!session) throw unauthorized('refresh_invalid', 'Сессия не найдена — войдите заново');

    if (session.revoked_at) {
      await db().query(
        'UPDATE sessions SET revoked_at = now() WHERE user_id = $1 AND revoked_at IS NULL',
        [session.user_id],
      );
      await logSecurityEvent({
        userId: session.user_id,
        eventType: 'refresh_reuse_detected',
        ip: clientIp(req),
        details: { session_id: session.id },
      });
      throw unauthorized('refresh_reuse', 'Обнаружено повторное использование токена — все сессии отозваны');
    }
    if (session.client_type !== body.client_type) {
      throw unauthorized('refresh_invalid', 'Сессия другого типа клиента');
    }
    const expiresAt = new Date(session.expires_at);
    if (!Number.isFinite(expiresAt.getTime()) || expiresAt.getTime() <= Date.now()) {
      throw unauthorized('refresh_expired', 'Сессия истекла — войдите заново');
    }

    const users = await db().query('SELECT * FROM users WHERE id = $1', [session.user_id]);
    const user = users[0];
    if (!user || user.is_banned) throw forbidden('user_banned', 'Аккаунт заблокирован');

    const { token: nextRefresh, hash: nextHash } = newRefreshToken();
    const nextExpiry = new Date(Date.now() + config.refreshTtlSec * 1000);
    await db().tx(async (tx) => {
      await tx.query('UPDATE sessions SET revoked_at = now() WHERE id = $1', [session.id]);
      await tx.query(
        `INSERT INTO sessions (user_id, refresh_token_hash, client_type, hwid, ip, user_agent, expires_at)
         VALUES ($1, $2, $3, $4, $5, $6, $7)`,
        [
          user.id,
          nextHash,
          session.client_type,
          session.hwid,
          clientIp(req),
          session.user_agent,
          nextExpiry,
        ],
      );
    });

    const accessToken = signAccessToken({
      userId: user.id,
      clientType: session.client_type,
      hwidHash: session.hwid ? sha256hex(session.hwid) : null,
      hwidTrusted: true,
    });

    res.json({
      access_token: accessToken,
      refresh_token: nextRefresh,
      token_type: 'Bearer',
      expires_in: config.accessTtlSec,
      subscription: subscriptionSummary(await getEffectiveSubscription(user.id)),
    });
  }),
);

router.post(
  '/logout',
  ah(async (req, res) => {
    const body = parse(logoutSchema, req.body);
    await db().query(
      'UPDATE sessions SET revoked_at = now() WHERE refresh_token_hash = $1 AND revoked_at IS NULL',
      [hashRefreshToken(body.refresh_token)],
    );
    res.json({ ok: true });
  }),
);

export default router;
