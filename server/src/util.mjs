import crypto from 'node:crypto';
import dns from 'node:dns/promises';
import { config } from './config.mjs';

export function sha256hex(value) {
  return crypto.createHash('sha256').update(value).digest('hex');
}

export function hmacHex(secret, value) {
  return crypto.createHmac('sha256', secret).update(value).digest('hex');
}

export function timingSafeEqualHex(a, b) {
  if (typeof a !== 'string' || typeof b !== 'string' || a.length !== b.length) return false;
  const bufA = Buffer.from(a, 'utf8');
  const bufB = Buffer.from(b, 'utf8');
  return crypto.timingSafeEqual(bufA, bufB);
}

export function normalizeEmail(raw) {
  return String(raw ?? '').trim().toLowerCase();
}

export function clientIp(req) {
  return (req.headers['x-forwarded-for'] ?? '').split(',')[0].trim()
    || req.socket?.remoteAddress
    || 'unknown';
}

const DISPOSABLE_DOMAINS = new Set([
  'mailinator.com', 'guerrillamail.com', 'guerrillamailblock.com', 'grr.la', 'guerrillamail.info',
  'guerrillamail.net', 'guerrillamail.org', 'guerrillamail.de', 'guerrillamail.biz',
  '10minutemail.com', '10minutemail.net', 'tempmail.com', 'temp-mail.org', 'temp-mail.io',
  'throwawaymail.com', 'yopmail.com', 'yopmail.fr', 'sharklasers.com', 'trashmail.com',
  'getnada.com', 'dispostable.com', 'maildrop.cc', 'fakeinbox.com', 'mintemail.com',
  'mytemp.email', 'tempinbox.com', 'spamgourmet.com', 'mailnesia.com', 'burnermail.io',
  'inboxkitten.com', 'mozmail.com', 'emailondeck.com', '33mail.com', 'mailcatch.com',
  'tempail.com', 'linshiyou.com', 'mail.tm', 'dropmail.me', 'minuteinbox.com',
]);

export function isDisposableEmail(email) {
  const domain = email.split('@')[1] ?? '';
  return DISPOSABLE_DOMAINS.has(domain);
}

export async function hasMxRecord(email) {
  const domain = email.split('@')[1] ?? '';
  if (!domain) return false;
  try {
    const records = await Promise.race([
      dns.resolveMx(domain),
      new Promise((_, reject) => setTimeout(() => reject(new Error('dns timeout')), 3000)),
    ]);
    return Array.isArray(records) && records.length > 0;
  } catch {
    return false;
  }
}

export async function checkEmailDomain(email) {
  if (isDisposableEmail(email)) return false;
  if (config.emailCheckMx && !(await hasMxRecord(email))) return false;
  return true;
}
