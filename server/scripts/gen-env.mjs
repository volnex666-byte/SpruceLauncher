import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const target = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '.env');

if (fs.existsSync(target) && !process.argv.includes('--force')) {
  console.log('.env already exists — pass --force to overwrite');
  process.exit(0);
}

const hex = (n = 32) => crypto.randomBytes(n).toString('hex');

const lines = [
  'NODE_ENV=development',
  'PORT=8080',
  'WEBSITE_ORIGINS=http://localhost:5173',
  'SITE_URL=http://localhost:5173',
  '',
  'DATABASE_URL=',
  'REDIS_URL=',
  'PGLITE=1',
  'PGLITE_DATA_DIR=.devdata/pg',
  'AUTO_MIGRATE=1',
  '',
  `JWT_SECRET=${hex()}`,
  `OTP_PEPPER=${hex()}`,
  `LAUNCHER_HMAC_SECRET=${hex()}`,
  `DEV_PEEK_TOKEN=${hex(24)}`,
  'HMAC_DISABLED=0',
  'AUTO_BAN_ON_SCOPE_VIOLATION=0',
  '',
  'SMTP_HOST=',
  'SMTP_PORT=587',
  'SMTP_USER=',
  'SMTP_PASS=',
  'MAIL_FROM=SpruceLauncher <no-reply@sprucelauncher.com>',
  'EMAIL_CHECK_MX=0',
];

fs.writeFileSync(target, lines.join('\n') + '\n');
console.log(`.env generated at ${target}`);
