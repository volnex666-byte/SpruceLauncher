import 'dotenv/config';
import crypto from 'node:crypto';
import path from 'node:path';

function bool(value, fallback = false) {
  if (value === undefined || value === '') return fallback;
  return value === '1' || String(value).toLowerCase() === 'true';
}

function int(value, fallback) {
  const parsed = Number.parseInt(value ?? '', 10);
  return Number.isFinite(parsed) ? parsed : fallback;
}

const nodeEnv = process.env.NODE_ENV ?? 'development';
const isProd = nodeEnv === 'production';

function secret(name, devFallback) {
  const value = process.env[name];
  if (value && value.trim()) return value.trim();
  if (isProd) throw new Error(`Required env ${name} is not set`);
  return devFallback ?? crypto.randomBytes(32).toString('hex');
}

export const config = {
  nodeEnv,
  isProd,
  devMode: !isProd,
  port: int(process.env.PORT, 8080),
  siteUrl: (process.env.SITE_URL ?? 'http://localhost:5173').replace(/\/+$/, ''),
  websiteOrigins: (process.env.WEBSITE_ORIGINS ?? 'http://localhost:5173')
    .split(',').map((s) => s.trim()).filter(Boolean),

  db: {
    url: (process.env.DATABASE_URL ?? '').trim(),
    usePglite: bool(process.env.PGLITE, true),
    pgliteDir: path.resolve(process.cwd(), process.env.PGLITE_DATA_DIR ?? '.devdata/pg'),
  },
  redis: {
    url: (process.env.REDIS_URL ?? '').trim(),
  },
  autoMigrate: bool(process.env.AUTO_MIGRATE, true),

  jwtSecret: secret('JWT_SECRET'),
  otpPepper: secret('OTP_PEPPER'),
  launcherHmacSecret: secret('LAUNCHER_HMAC_SECRET'),
  hmacDisabled: bool(process.env.HMAC_DISABLED, false),
  autoBanOnScopeViolation: bool(process.env.AUTO_BAN_ON_SCOPE_VIOLATION, false),
  devPeekToken: (process.env.DEV_PEEK_TOKEN ?? '').trim(),

  smtp: {
    host: (process.env.SMTP_HOST ?? '').trim(),
    port: int(process.env.SMTP_PORT, 587),
    user: (process.env.SMTP_USER ?? '').trim(),
    pass: (process.env.SMTP_PASS ?? '').trim(),
    from: (process.env.MAIL_FROM ?? 'SpruceLauncher <no-reply@sprucelauncher.com>').trim(),
  },
  emailCheckMx: bool(process.env.EMAIL_CHECK_MX, false),

  accessTtlSec: 15 * 60,
  refreshTtlSec: 7 * 24 * 60 * 60,
  otpTtlSec: 10 * 60,
  otpMaxAttempts: 3,
};
