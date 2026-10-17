# Деплой SpruceLauncher в интернет (бесплатно, 24/7)

Схема деплоя:

| Часть | Где размещается | Цена |
|---|---|---|
| Код проекта | GitHub (репозиторий) | 0 ₽ |
| Сайт (лендинг + кабинет) | GitHub Pages | 0 ₽ |
| Бэкенд (API) | Render (или Railway) | 0 ₽ |
| База данных Postgres | Neon | 0 ₽ |
| Redis (опционально) | Upstash | 0 ₽ |
| Почта (коды входа) | Brevo / Gmail | 0 ₽ |

После настройки: сайт открывается у любого пользователя 24/7, независимо от твоего ПК.

---

## Шаг 1. GitHub

1. Зарегистрируйся на https://github.com (если нет аккаунта).
2. Создай **публичный** репозиторий, например `SpruceLauncher`
   (публичный — потому что GitHub Pages для приватных репозиториев требует платный аккаунт).
3. Запушь проект. В PowerShell из папки `D:\SpruceLauncher`:

```powershell
git push -u origin main
```

(репозиторий уже инициализирован и закоммичен; команду `git remote add origin https://github.com/ТВОЙ_ЛОГИН/SpruceLauncher.git` нужно выполнить один раз перед пушем)

При первом пуше Git попросит войти в GitHub — откроется окно браузера, подтверди.

---

## Шаг 2. Neon — база данных Postgres

1. Зайди на https://neon.tech → Sign up with GitHub.
2. Create project → имя `sprucelauncher`, регион `Frankfurt` (ближе к России).
3. Скопируй **Connection string** вида:
   `postgresql://user:pass@ep-xxx.eu-central-1.aws.neon.tech/neondb?sslmode=require`
4. Сохрани её — понадобится на следующем шаге.

---

## Шаг 3. Render — бэкенд (API)

1. Зайди на https://render.com → Sign up with GitHub.
2. New → **Blueprint** → выбери репозиторий `SpruceLauncher` → Apply.
   Файл `render.yaml` в корне репозитория создаст сервис автоматически.
3. Заполни переменные (Render спросит сам):

| Переменная | Значение |
|---|---|
| `DATABASE_URL` | строка подключения из Neon (шаг 2) |
| `WEBSITE_ORIGINS` | `https://ТВОЙ_ЛОГИН.github.io` (без слэша и без пути!) |
| `SITE_URL` | `https://ТВОЙ_ЛОГИН.github.io/SpruceLauncher` |
| `SMTP_HOST` … `MAIL_FROM` | данные почты (шаг 5; можно заполнить позже) |

`JWT_SECRET`, `OTP_PEPPER`, `LAUNCHER_HMAC_SECRET` создаются автоматически.

4. Дождись деплоя и проверь здоровье сервиса:
   `https://ИМЯ-СЕРВИСА.onrender.com/health` → должно вернуть `{"ok":true,...}`
5. Скопируй адрес бэкенда — понадобится в шаге 4.

> Бесплатный тариф Render засыпает после ~15 минут без запросов (первый запрос
> будит сервер ~50 секунд). Чтобы сайт не «засыпал», зарегистрируйся на
> https://uptimerobot.com (бесплатно) и поставь HTTP-монитор на
> `https://ИМЯ-СЕРВИСА.onrender.com/health` каждые 5 минут.

### Альтернатива: Railway
1. https://railway.app → Sign up with GitHub.
2. New Project → Deploy from GitHub repo → выбери `SpruceLauncher`.
3. В настройках сервиса укажи **Root Directory: `server`** — Railway сам найдёт `server/Dockerfile`.
4. Добавь те же переменные, что в таблице выше (+ `DATABASE_URL`).
5. Settings → Networking → Generate Domain → проверь `/health`.

---

## Шаг 4. GitHub Pages — сайт

1. В репозитории: **Settings → Pages → Source: GitHub Actions**.
2. **Settings → Secrets and variables → Actions → вкладка Variables → New repository variable**:
   - Name: `API_BASE`
   - Value: `https://ИМЯ-СЕРВИСА.onrender.com` (адрес бэкенда из шага 3)
3. Пуш в `main` запустит деплой автоматически. Если не запустился:
   Actions → «Deploy web to GitHub Pages» → Run workflow.
4. Сайт появится по адресу: `https://ТВОЙ_ЛОГИН.github.io/SpruceLauncher/`

> Если позже подключишь свой домен (Settings → Pages → Custom domain),
> поменяй в `.github/workflows/deploy-web.yml` строку `VITE_BASE: /.../` на `VITE_BASE: /`
> и добавь домен в `WEBSITE_ORIGINS` на Render.

---

## Шаг 5. Почта — чтобы коды входа приходили на email

Без почты сайт работает только в dev-режиме (код показывается на экране),
а в production код никуда не отправится — вход будет невозможен. Настрой один сервис:

### Вариант А: Brevo (рекомендуется, 300 писем/день бесплатно)
1. https://brevo.com → регистрация.
2. SMTP & API → SMTP: сервер `smtp-relay.brevo.com`, порт `587`.
3. Создай SMTP key. В Render добавь:

```
SMTP_HOST=smtp-relay.brevo.com
SMTP_PORT=587
SMTP_USER=твой_логин_из_brevo
SMTP_PASS=SMTP_key
MAIL_FROM=SpruceLauncher <твоя_почта>
```

### Вариант Б: Gmail (нужен «пароль приложения»)
1. В Google-аккаунте включи двухэтапную аутентификацию.
2. Создай пароль приложения: https://myaccount.google.com/apppasswords
3. В Render добавь `SMTP_HOST=smtp.gmail.com`, `SMTP_PORT=587`,
   `SMTP_USER=твоя_почта@gmail.com`, `SMTP_PASS=пароль_приложения`.

После добавления Render перезапустит бэкенд — жёлтая dev-плашка на сайте исчезнет,
коды начнут приходить письмами.

---

## Шаг 6. Проверка

1. Открой `https://ТВОЙ_ЛОГИН.github.io/SpruceLauncher/` — должен открыться лендинг.
2. Создай аккаунт своей почтой → код придёт письмом → входи.
3. В кабинете: профили, подписка, защита — всё должно работать.
4. Проверь, что тарифы отображаются: Обычный 50 ₽/мес и Креатор 320 ₽/мес.

---

## Шаг 7 (опционально). Redis — чтобы коды и лимиты не терялись при перезапуске

1. https://upstash.com → регистрация → Create Database (регион Frankfurt).
2. Скопируй `REDIS_URL` (вида `rediss://default:...@...upstash.io:6379`).
3. Добавь переменную `REDIS_URL` в Render.

Без Redis всё работает, но активные коды входа и счётчики лимитов сбрасываются
при каждом перезапуске сервера (на Neon база сохраняется всегда).

---

## Обновления проекта

```powershell
git add .
git commit -m "описание изменений"
git push
```

- Сайт: GitHub Pages пересоберётся сам (workflow на каждый пуш).
- Бэкенд: Render/Railway пересоберут сам (autoDeploy).

## Частые проблемы

| Симптом | Причина | Решение |
|---|---|---|
| На сайте «Ошибка сети» | Не задан `API_BASE` | Variables → Actions → `API_BASE` |
| CORS-ошибка в консоли браузера | `WEBSITE_ORIGINS` не совпадает | Указать `https://ЛОГИН.github.io` без слэша |
| 404 при заходе на `/login` | Не включены Pages через Actions | Settings → Pages → Source: GitHub Actions |
| Бэкенд долго отвечает первый раз | Render free «спит» | UptimeRobot (шаг 3) |
| Код входа не приходит | Не настроен SMTP | Шаг 5 |

## Локальная разработка (не изменилась)

```powershell
# бэкенд
cd server; node src/index.mjs

# сайт
cd web; npm run dev     # http://localhost:5173
```

Локально `VITE_BASE`/`VITE_API_BASE` не задаются — работают прокси Vite и dev-плашка с кодом.
