import crypto from 'node:crypto';
import { config } from '../src/config.mjs';

const BASE = `http://localhost:${config.port}`;
const DEV_HEADERS = { 'X-Dev-Token': config.devPeekToken };

let passed = 0;
let failed = 0;

function check(name, condition, extra = '') {
  if (condition) {
    passed += 1;
    console.log(`  ok   ${name}`);
  } else {
    failed += 1;
    console.log(`  FAIL ${name}${extra ? ` — ${extra}` : ''}`);
  }
}

function sha256(value) {
  return crypto.createHash('sha256').update(value).digest('hex');
}

function signHmac(method, path, bodyStr, overrides = {}) {
  const timestamp = overrides.timestamp ?? String(Math.floor(Date.now() / 1000));
  const nonce = overrides.nonce ?? crypto.randomBytes(16).toString('hex');
  const bodyHash = sha256(bodyStr ?? '');
  const canonical = [method.toUpperCase(), path, timestamp, nonce, bodyHash].join('\n');
  const signature = overrides.signature
    ?? crypto.createHmac('sha256', config.launcherHmacSecret).update(canonical).digest('hex');
  return {
    'X-Client-Id': 'launcher',
    'X-Timestamp': timestamp,
    'X-Nonce': nonce,
    'X-Signature': signature,
  };
}

async function api(path, { method = 'GET', token, body, client, hmacHeaders, dev = false } = {}) {
  const bodyStr = body === undefined ? undefined : JSON.stringify(body);
  const headers = {};
  if (dev) Object.assign(headers, DEV_HEADERS);
  if (bodyStr !== undefined) headers['Content-Type'] = 'application/json';
  if (token) headers.Authorization = `Bearer ${token}`;
  if (client === 'launcher' && !config.hmacDisabled) {
    Object.assign(headers, hmacHeaders ?? signHmac(method, path, bodyStr));
  }
  const res = await fetch(BASE + path, { method, headers, body: bodyStr });
  let json = null;
  try {
    json = await res.json();
  } catch {
    json = null;
  }
  return { status: res.status, body: json };
}

const code = (result) => result.body?.error?.code;

async function main() {
  console.log(`Smoke test against ${BASE} (dev mode: HMAC_DISABLED=${config.hmacDisabled ? '1' : '0'})`);

  try {
    const health = await api('/health');
    if (health.status !== 200) throw new Error(`health returned ${health.status}`);
  } catch (err) {
    console.error(`Server is not reachable at ${BASE}. Start it with: npm start`);
    console.error(String(err));
    process.exit(1);
  }

  const email = `smoke-${Date.now()}@example.com`;
  const hwid = `smoke-hwid-${crypto.randomBytes(4).toString('hex')}`;

  console.log('\n[1] Website passwordless login');
  let res = await api('/auth/request-code', { method: 'POST', body: { email } });
  check('request-code returns ok', res.status === 200 && res.body?.ok === true, JSON.stringify(res.body));

  res = await api('/dev/peek-otp', { method: 'POST', body: { email }, dev: true });
  const websiteCode = res.body?.code;
  check('dev peek returns 6-digit code', res.status === 200 && /^\d{6}$/.test(websiteCode ?? ''));

  res = await api('/dev/peek-otp', { method: 'POST', body: { email } });
  check('dev peek without token is hidden (404)', res.status === 404);

  res = await api('/auth/verify-code', { method: 'POST', body: { email, code: '000000', client_type: 'website' } });
  check('wrong OTP rejected', res.status === 401 && code(res) === 'code_invalid');

  res = await api('/auth/verify-code', { method: 'POST', body: { email, code: websiteCode, client_type: 'website' } });
  const websiteToken = res.body?.access_token;
  const websiteRefresh = res.body?.refresh_token;
  check('website login ok', res.status === 200 && Boolean(websiteToken) && Boolean(websiteRefresh));
  check('free subscription on login', res.body?.subscription?.plan_type === 'free' && res.body?.subscription?.can_play === true);

  res = await api('/auth/verify-code', { method: 'POST', body: { email, code: websiteCode, client_type: 'website' } });
  check('OTP cannot be reused', res.status === 401 && code(res) === 'code_invalid');

  console.log('\n[2] Website cabinet (read/write)');
  res = await api('/api/website/me', { token: websiteToken });
  check('GET /api/website/me works', res.status === 200 && res.body?.user?.email === email, JSON.stringify(res.body));

  res = await api('/api/website/profiles', {
    method: 'POST', token: websiteToken,
    body: { name: 'Smoke', mc_version: '1.21.4', loader: 'fabric', ram_mb: 4096 },
  });
  check('RAM over free limit blocked', res.status === 403 && code(res) === 'plan_limit_ram', JSON.stringify(res.body));

  res = await api('/api/website/profiles', {
    method: 'POST', token: websiteToken,
    body: { name: 'Smoke', mc_version: '1.21.4', loader: 'fabric', ram_mb: 2048 },
  });
  const profileId = res.body?.profile?.id;
  check('profile create works (201)', res.status === 201 && Boolean(profileId));
  check('first profile becomes default', res.body?.profile?.is_default === true);

  res = await api('/api/website/profiles', {
    method: 'POST', token: websiteToken,
    body: { name: 'Smoke 2', mc_version: '1.21.4', ram_mb: 2048 },
  });
  check('profile count limit enforced on free plan', res.status === 403 && code(res) === 'plan_limit_profiles');

  res = await api(`/api/website/profiles/${profileId}`, {
    method: 'PATCH', token: websiteToken,
    body: { name: 'Smoke 2' },
  });
  check('profile rename works', res.status === 200 && res.body?.profile?.name === 'Smoke 2');

  res = await api('/api/website/profiles', { token: websiteToken });
  check('profile list shows 1 profile', res.status === 200 && res.body?.profiles?.length === 1);

  console.log('\n[3] Launcher thin client (read-only)');
  res = await api('/auth/request-code', { method: 'POST', body: { email } });
  res = await api('/dev/peek-otp', { method: 'POST', body: { email }, dev: true });
  const launcherCode = res.body?.code;

  res = await api('/auth/verify-code', {
    method: 'POST',
    body: { email, code: launcherCode, client_type: 'launcher', hwid },
  });
  const launcherToken = res.body?.access_token;
  const launcherRefresh = res.body?.refresh_token;
  check('launcher login ok', res.status === 200 && Boolean(launcherToken));
  check('hwid bound on first launcher login', res.body?.hwid_trusted === true);

  res = await api('/api/launcher/me', { token: launcherToken, client: 'launcher' });
  check('GET /api/launcher/me works with valid HMAC', res.status === 200 && res.body?.user?.hwid_bound === true, JSON.stringify(res.body));

  res = await api('/api/launcher/profiles', { token: launcherToken, client: 'launcher' });
  check('launcher sees website profiles', res.status === 200 && res.body?.profiles?.[0]?.name === 'Smoke 2');

  res = await api('/api/launcher/subscription', { token: launcherToken, client: 'launcher' });
  check('subscription endpoint reports free/active', res.status === 200 && res.body?.subscription?.plan_type === 'free' && res.body?.subscription?.can_play === true);

  res = await api('/api/launcher/me', { token: launcherToken });
  check('unsigned launcher request rejected', res.status === 401 && code(res) === 'hmac_required');

  console.log('\n[4] HMAC hardening');
  const badSig = signHmac('GET', '/api/launcher/me', undefined, { signature: 'ab'.repeat(32) });
  res = await api('/api/launcher/me', { token: launcherToken, client: 'launcher', hmacHeaders: badSig });
  check('invalid signature rejected', res.status === 401 && code(res) === 'hmac_invalid');

  const stale = signHmac('GET', '/api/launcher/me', undefined, { timestamp: String(Math.floor(Date.now() / 1000) - 3600) });
  res = await api('/api/launcher/me', { token: launcherToken, client: 'launcher', hmacHeaders: stale });
  check('stale timestamp rejected', res.status === 401 && code(res) === 'hmac_stale');

  const replayHeaders = signHmac('GET', '/api/launcher/me', undefined);
  res = await api('/api/launcher/me', { token: launcherToken, client: 'launcher', hmacHeaders: replayHeaders });
  check('first signed request accepted', res.status === 200);
  res = await api('/api/launcher/me', { token: launcherToken, client: 'launcher', hmacHeaders: replayHeaders });
  check('nonce replay rejected', res.status === 401 && code(res) === 'hmac_replay');

  console.log('\n[5] Cross-client access control');
  res = await api('/api/launcher/me', { token: websiteToken, client: 'launcher' });
  check('website token cannot use launcher API', res.status === 401);

  res = await api('/api/website/me', { token: launcherToken });
  check('launcher token cannot use website API', res.status === 401);

  res = await api('/api/launcher/profiles', {
    method: 'POST', token: launcherToken, client: 'launcher',
    body: { name: 'Hack', mc_version: '1.21.4' },
  });
  check('launcher POST blocked (read-only client)', res.status === 403 && code(res) === 'read_only_client', JSON.stringify(res.body));

  console.log('\n[6] Plans and expiry');
  res = await api('/dev/activate-subscription', {
    method: 'POST', dev: true,
    body: { email, plan_type: 'premium', days: 30 },
  });
  check('dev activation ok', res.status === 200 && res.body?.ok === true);

  res = await api('/api/launcher/subscription', { token: launcherToken, client: 'launcher' });
  check('premium plan visible to launcher', res.status === 200 && res.body?.subscription?.plan_type === 'premium');
  check('premium limits raised (RAM 8192)', res.body?.subscription?.limits?.max_ram_mb === 8192);

  res = await api('/api/website/profiles', {
    method: 'POST', token: websiteToken,
    body: { name: 'High RAM', mc_version: '1.21.4', ram_mb: 8192 },
  });
  check('premium allows 8 GB profile', res.status === 201);

  res = await api('/api/website/profiles', {
    method: 'POST', token: websiteToken,
    body: { name: 'Too much RAM', mc_version: '1.21.4', ram_mb: 8193 },
  });
  check('RAM above premium limit blocked', res.status === 403 && code(res) === 'plan_limit_ram');

  res = await api('/dev/activate-subscription', {
    method: 'POST', dev: true,
    body: { email, plan_type: 'premium', expired: true },
  });
  res = await api('/api/launcher/subscription', { token: launcherToken, client: 'launcher' });
  check('expired subscription blocks play', res.status === 200
    && res.body?.subscription?.can_play === false
    && res.body?.subscription?.status === 'expired', JSON.stringify(res.body?.subscription));

  res = await api('/dev/activate-subscription', {
    method: 'POST', dev: true,
    body: { email, plan_type: 'legendary', days: 10 },
  });
  res = await api('/api/launcher/subscription', { token: launcherToken, client: 'launcher' });
  check('reactivation restores play', res.body?.subscription?.can_play === true
    && res.body?.subscription?.plan_type === 'legendary');

  console.log('\n[7] Refresh rotation and reuse detection');
  res = await api('/auth/refresh', { method: 'POST', body: { refresh_token: launcherRefresh, client_type: 'launcher' } });
  const rotated = res.body?.refresh_token;
  check('refresh rotates tokens', res.status === 200 && Boolean(res.body?.access_token) && rotated !== launcherRefresh);

  res = await api('/auth/refresh', { method: 'POST', body: { refresh_token: launcherRefresh, client_type: 'launcher' } });
  check('reused refresh token detected', res.status === 401 && code(res) === 'refresh_reuse');

  res = await api('/auth/refresh', { method: 'POST', body: { refresh_token: rotated, client_type: 'launcher' } });
  check('all sessions revoked after reuse', res.status === 401);

  res = await api('/auth/logout', { method: 'POST', body: { refresh_token: websiteRefresh } });
  check('logout returns ok', res.status === 200 && res.body?.ok === true);

  console.log(`\n${passed} passed, ${failed} failed`);
  process.exit(failed ? 1 : 0);
}

main().catch((err) => {
  console.error('Smoke test crashed:', err);
  process.exit(1);
});
