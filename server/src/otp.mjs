import crypto from 'node:crypto';
import { config } from './config.mjs';
import { db } from './db.mjs';
import { hmacHex, timingSafeEqualHex } from './util.mjs';

export async function createOtp(userId) {
  const code = String(crypto.randomInt(0, 1_000_000)).padStart(6, '0');
  const codeHash = hmacHex(config.otpPepper, `otp:${userId}:${code}`);
  await db().tx(async (tx) => {
    await tx.query(
      'UPDATE otp_codes SET consumed_at = now() WHERE user_id = $1 AND consumed_at IS NULL',
      [userId],
    );
    await tx.query(
      `INSERT INTO otp_codes (user_id, code_hash, expires_at)
       VALUES ($1, $2, now() + ($3::int * interval '1 second'))`,
      [userId, codeHash, config.otpTtlSec],
    );
  });
  return code;
}

export async function verifyOtp(userId, code) {
  const rows = await db().query(
    `SELECT id, code_hash, attempts FROM otp_codes
     WHERE user_id = $1 AND consumed_at IS NULL AND expires_at > now()
     ORDER BY created_at DESC LIMIT 1`,
    [userId],
  );
  const otp = rows[0];
  if (!otp) return { ok: false, reason: 'no_active_code' };
  if (otp.attempts >= config.otpMaxAttempts) {
    await db().query('UPDATE otp_codes SET consumed_at = now() WHERE id = $1', [otp.id]);
    return { ok: false, reason: 'too_many_attempts' };
  }
  const candidate = hmacHex(config.otpPepper, `otp:${userId}:${String(code)}`);
  if (!timingSafeEqualHex(candidate, otp.code_hash)) {
    await db().query('UPDATE otp_codes SET attempts = attempts + 1 WHERE id = $1', [otp.id]);
    return { ok: false, reason: 'wrong_code' };
  }
  await db().query('UPDATE otp_codes SET consumed_at = now() WHERE id = $1', [otp.id]);
  return { ok: true };
}
