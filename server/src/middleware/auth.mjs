import jwt from 'jsonwebtoken';
import { db } from '../db.mjs';
import { kv } from '../kv.mjs';
import { verifyAccessToken } from '../jwt.mjs';
import { unauthorized, forbidden, tooMany } from '../errors.mjs';
import { clientIp } from '../util.mjs';
import { logSecurityEvent } from './guard.mjs';

const USER_MINUTE_LIMIT = 100;

export function requireAuth(expectedClientType) {
  return async (req, res, next) => {
    try {
      const header = req.get('authorization') ?? '';
      const token = header.startsWith('Bearer ') ? header.slice(7) : null;
      if (!token) throw unauthorized();

      let payload;
      try {
        payload = verifyAccessToken(token, expectedClientType);
      } catch {
        const decoded = jwt.decode(token);
        if (decoded && decoded.ctype === 'launcher' && expectedClientType === 'website') {
          await logSecurityEvent({
            userId: Number.isFinite(Number(decoded.sub)) ? Number(decoded.sub) : null,
            hwidHash: decoded.hwid ?? null,
            ip: clientIp(req),
            eventType: 'cross_client_access',
            details: { method: req.method, path: req.originalUrl },
          });
        }
        throw unauthorized('token_invalid', 'Сессия истекла — войдите заново');
      }

      if (payload.ctype !== expectedClientType) {
        throw forbidden('wrong_client', 'Токен другого типа клиента');
      }

      const stateKey = `user:state:${payload.sub}`;
      let state = await kv().get(stateKey);
      if (state === null) {
        const rows = await db().query('SELECT is_banned FROM users WHERE id = $1', [payload.sub]);
        if (!rows.length) throw unauthorized('user_missing', 'Пользователь не найден');
        state = rows[0].is_banned ? 'banned' : 'ok';
        await kv().set(stateKey, state, 60);
      }
      if (state === 'banned') throw forbidden('user_banned', 'Аккаунт заблокирован');

      if (payload.hwid) {
        const banKey = `hwid:ban:${payload.hwid}`;
        let banned = await kv().get(banKey);
        if (banned === null) {
          const rows = await db().query(
            `SELECT 1 FROM hwid_bans hb
             JOIN users u ON u.id = $1
             WHERE hb.hwid = u.hwid LIMIT 1`,
            [payload.sub],
          );
          banned = rows.length ? '1' : '0';
          await kv().set(banKey, banned, 300);
        }
        if (banned === '1') throw forbidden('hwid_banned', 'Устройство заблокировано');
      }

      const minuteBucket = Math.floor(Date.now() / 60_000);
      const accountHits = await kv().incr(`rl:user:${payload.sub}:${minuteBucket}`, 120);
      if (accountHits > USER_MINUTE_LIMIT) throw tooMany();

      req.user = {
        id: Number(payload.sub),
        scope: payload.scope,
        clientType: payload.ctype,
        hwidHash: payload.hwid ?? null,
        hwidTrusted: Boolean(payload.hwid_trusted),
        ip: clientIp(req),
      };
      next();
    } catch (err) {
      next(err);
    }
  };
}

export function requireScope(scope) {
  return (req, res, next) => {
    if (!req.user || !req.user.scope.includes(scope)) {
      return next(forbidden('insufficient_scope', 'Недостаточно прав токена'));
    }
    next();
  };
}
