import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { firefox } from 'playwright';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const outDir = path.join(root, 'screenshots');
fs.mkdirSync(outDir, { recursive: true });

function readDevToken() {
  const envPath = path.resolve(root, '..', 'server', '.env');
  const content = fs.readFileSync(envPath, 'utf8');
  const match = content.match(/^DEV_PEEK_TOKEN=(.+)$/m);
  if (!match) throw new Error('DEV_PEEK_TOKEN not found in server/.env');
  return match[1].trim();
}

const BASE = process.env.WEB_BASE ?? 'http://localhost:5173';
const API = process.env.API_BASE ?? 'http://localhost:8080';
const devToken = readDevToken();
const email = `ui-${Date.now()}@example.com`;

const errors = [];
const browser = await firefox.launch();
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
page.on('pageerror', (err) => errors.push(`pageerror: ${err.message}`));
page.on('console', (msg) => {
  if (msg.type() === 'error') errors.push(`console: ${msg.text()}`);
});
page.setDefaultTimeout(20000);

async function shot(name) {
  await page.screenshot({ path: path.join(outDir, name), fullPage: true });
  console.log(`shot ${name}`);
}

async function devPost(route, body) {
  const res = await fetch(`${API}${route}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-Dev-Token': devToken },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(`${route} -> ${res.status}`);
  return res.json();
}

try {
  console.log(`Firefox smoke: ${BASE} as ${email}`);

  await page.goto(`${BASE}/`);
  await page.getByRole('heading', { name: /ДОБРО ПОЖАЛОВАТЬ/ }).waitFor();
  await shot('00-landing.png');
  await page.getByRole('heading', { name: 'Возможности' }).waitFor();
  await page.getByRole('link', { name: 'Как начать' }).click();
  await page.getByRole('heading', { name: /ДОБРО ПОЖАЛОВАТЬ/ }).waitFor();
  await page.getByRole('link', { name: 'Тарифы' }).click();
  await page.locator('#plans').waitFor();
  await shot('00-landing-plans.png');

  await page.getByRole('link', { name: 'Создать аккаунт' }).first().click();
  await page.getByPlaceholder('you@example.com').waitFor();
  await page.getByText(/создадим аккаунт/).waitFor();
  await shot('00-register.png');
  await page.getByPlaceholder('you@example.com').fill(email);
  await page.getByRole('button', { name: 'Создать аккаунт' }).click();
  await page.locator('input[inputmode="numeric"]').waitFor();
  await page.getByText(/Ваш код/).first().waitFor();
  await shot('00-code.png');

  const { code } = await devPost('/dev/peek-otp', { email });
  await page.locator('input[inputmode="numeric"]').fill(code);
  await page.getByRole('button', { name: 'Подтвердить и создать' }).click();
  await page.getByRole('heading', { name: 'Обзор' }).waitFor();
  await page.getByText('Аккаунт создан').waitFor();
  await shot('01-overview.png');

  await page.getByRole('link', { name: 'Профили', exact: true }).click();
  await page.getByRole('button', { name: 'Создать профиль' }).first().click();
  await page.getByLabel('Название').fill('UI тест');
  await page.getByRole('button', { name: 'Сохранить' }).click();
  await page.getByText('UI тест').first().waitFor();
  await shot('02-profiles.png');

  await devPost('/dev/activate-subscription', { email, plan_type: 'premium', days: 30 });

  await page.getByRole('link', { name: 'Подписка', exact: true }).click();
  await page.getByRole('heading', { name: 'Подписка' }).waitFor();
  await page.getByText('Креатор').first().waitFor();
  await shot('03-subscription.png');

  await page.getByRole('link', { name: 'Защита', exact: true }).click();
  await page.getByRole('heading', { name: 'Защита' }).waitFor();
  await page.getByText('Активные сессии', { exact: true }).waitFor();
  await page.locator('text=Загрузка…').first().waitFor({ state: 'detached' });
  await page.getByText('Успешно', { exact: true }).first().waitFor();
  await shot('04-security.png');

  const realErrors = errors.filter((text) => !/favicon/i.test(text));
  if (realErrors.length) {
    console.log('Browser errors:');
    for (const err of realErrors) console.log(`  - ${err}`);
  } else {
    console.log('No browser errors');
  }
  console.log('Done');
} catch (err) {
  console.error('Firefox smoke failed:', err.message);
  await page.screenshot({ path: path.join(outDir, '90-failure.png'), fullPage: true }).catch(() => {});
  process.exitCode = 1;
} finally {
  await browser.close();
}
