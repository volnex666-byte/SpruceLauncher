import { config } from '../config.mjs';
import { kv } from '../kv.mjs';
import { hmacHex, sha256hex, timingSafeEqualHex } from '../util.mjs';
import { unauthorized } from '../errors.mjs';

const TIMESTAMP_WINDOW_SEC = 60;
const NONCE_TTL_SEC = 300;

async function handle(req, res, next) {
  if (req.get('x-client-id') !== 'launcher') return next();
  if (config.hmacDisabled && config.devMode) return next();

  const timestamp = req.get('x-timestamp');
  const nonce = req.get('x-nonce');
  const signature = req.get('x-signature');
  if (!timestamp || !nonce || !signature) {
    return next(unauthorized('hmac_missing', 'Отсутствует подпись клиента'));
  }

  const nowSec = Math.floor(Date.now() / 1000);
  const ts = Number.parseInt(timestamp, 10);
  if (!Number.isFinite(ts) || Math.abs(nowSec - ts) > TIMESTAMP_WINDOW_SEC) {
    return next(unauthorized('hmac_stale', 'Подпись запроса устарела'));
  }

  const bodyHash = sha256hex(req.rawBody ?? Buffer.alloc(0));
  const canonical = [req.method.toUpperCase(), req.originalUrl, timestamp, nonce, bodyHash].join('\n');
  const expected = hmacHex(config.launcherHmacSecret, canonical);
  if (!timingSafeEqualHex(expected, signature)) {
    return next(unauthorized('hmac_invalid', 'Неверная подпись запроса'));
  }

  if (!(await kv().setIfAbsent(`hmac:nonce:${nonce}`, '1', NONCE_TTL_SEC))) {
    return next(unauthorized('hmac_replay', 'Повторный запрос отклонён'));
  }

  req.hmacVerified = true;
  next();
}

export function launcherHmac(req, res, next) {
  handle(req, res, next).catch(next);
}

export function requireLauncherSignature(req, res, next) {
  if (req.user?.clientType !== 'launcher') return next();
  if (config.hmacDisabled && config.devMode) return next();
  if (!req.hmacVerified) {
    return next(unauthorized('hmac_required', 'Запросы лаунчера должны быть подписаны'));
  }
  next();
}
