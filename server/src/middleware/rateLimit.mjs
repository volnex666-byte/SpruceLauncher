import { kv } from '../kv.mjs';
import { tooMany } from '../errors.mjs';
import { clientIp, normalizeEmail } from '../util.mjs';

export function rateLimit({ name, limit, windowSec, keyFn }) {
  return async (req, res, next) => {
    try {
      const bucket = Math.floor(Date.now() / (windowSec * 1000));
      const key = `rl:${name}:${keyFn(req)}:${bucket}`;
      const hits = await kv().incr(key, windowSec + 1);
      if (hits > limit) {
        res.set('Retry-After', String(windowSec));
        return next(tooMany());
      }
      next();
    } catch (err) {
      next(err);
    }
  };
}

export const byIp = (req) => clientIp(req);
export const byBodyEmail = (req) => normalizeEmail(req.body?.email) || `ip:${clientIp(req)}`;
