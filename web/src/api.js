const ACCESS_KEY = 'spruce_access';
const REFRESH_KEY = 'spruce_refresh';

const API_BASE = String(import.meta.env.VITE_API_BASE ?? '').replace(/\/+$/, '');
const apiUrl = (path) => `${API_BASE}${path}`;

export class ApiError extends Error {
  constructor(status, code, message, details) {
    super(message);
    this.status = status;
    this.code = code;
    this.details = details;
  }
}

export const tokens = {
  get access() {
    return localStorage.getItem(ACCESS_KEY);
  },
  get refresh() {
    return localStorage.getItem(REFRESH_KEY);
  },
  set(data) {
    if (data?.access_token) localStorage.setItem(ACCESS_KEY, data.access_token);
    if (data?.refresh_token) localStorage.setItem(REFRESH_KEY, data.refresh_token);
  },
  clear() {
    localStorage.removeItem(ACCESS_KEY);
    localStorage.removeItem(REFRESH_KEY);
  },
};

let refreshing = null;

async function doRefresh() {
  const refreshToken = tokens.refresh;
  if (!refreshToken) throw new ApiError(401, 'no_refresh', 'Нет refresh-токена');
  const res = await fetch(apiUrl('/auth/refresh'), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refresh_token: refreshToken, client_type: 'website' }),
  });
  if (!res.ok) {
    tokens.clear();
    throw new ApiError(res.status, 'refresh_failed', 'Сессия истекла — войдите заново');
  }
  const data = await res.json();
  tokens.set(data);
  return data;
}

function refreshOnce() {
  if (!refreshing) {
    refreshing = doRefresh().finally(() => {
      refreshing = null;
    });
  }
  return refreshing;
}

export async function api(path, { method = 'GET', body, auth = true, retry = true } = {}) {
  const headers = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (auth && tokens.access) headers.Authorization = `Bearer ${tokens.access}`;

  const res = await fetch(apiUrl(path), {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  if (res.status === 401 && auth && retry && tokens.refresh) {
    await refreshOnce();
    return api(path, { method, body, auth, retry: false });
  }

  let data = null;
  try {
    data = await res.json();
  } catch {
    data = null;
  }

  if (!res.ok) {
    throw new ApiError(
      res.status,
      data?.error?.code ?? 'error',
      data?.error?.message ?? `Ошибка ${res.status}`,
      data?.error?.details,
    );
  }
  return data;
}

export const requestCode = (email) =>
  api('/auth/request-code', { method: 'POST', body: { email }, auth: false });

export const verifyCode = (email, code) =>
  api('/auth/verify-code', {
    method: 'POST',
    body: { email, code, client_type: 'website' },
    auth: false,
  });
