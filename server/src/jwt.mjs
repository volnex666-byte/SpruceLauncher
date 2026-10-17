import crypto from 'node:crypto';
import jwt from 'jsonwebtoken';
import { config } from './config.mjs';

export const SCOPES = {
  website: ['website:read', 'website:write'],
  launcher: ['launcher:read'],
};

export function signAccessToken({ userId, clientType, hwidHash, hwidTrusted }) {
  return jwt.sign(
    {
      scope: SCOPES[clientType],
      ctype: clientType,
      hwid: hwidHash ?? null,
      hwid_trusted: Boolean(hwidTrusted),
    },
    config.jwtSecret,
    {
      subject: String(userId),
      expiresIn: config.accessTtlSec,
      issuer: 'sprucelauncher',
      audience: clientType === 'launcher' ? 'launcher' : 'website',
    },
  );
}

export function verifyAccessToken(token, expectedClientType) {
  const payload = jwt.verify(token, config.jwtSecret, {
    issuer: 'sprucelauncher',
    audience: expectedClientType === 'launcher' ? 'launcher' : 'website',
  });
  if (!Array.isArray(payload.scope)) throw new Error('token has no scope');
  return payload;
}

export function newRefreshToken() {
  const token = crypto.randomBytes(48).toString('base64url');
  return { token, hash: crypto.createHash('sha256').update(token).digest('hex') };
}

export function hashRefreshToken(token) {
  return crypto.createHash('sha256').update(token).digest('hex');
}
