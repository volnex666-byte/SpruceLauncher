import { useCallback, useEffect, useState } from 'react';
import { api } from '../api.js';
import { useAuth } from '../auth.jsx';

function formatDate(value) {
  if (!value) return '—';
  return new Date(value).toLocaleString('ru-RU', {
    day: '2-digit',
    month: '2-digit',
    year: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}

function shortAgent(userAgent) {
  if (!userAgent) return '—';
  if (/firefox/i.test(userAgent)) return 'Firefox';
  if (/edg/i.test(userAgent)) return 'Edge';
  if (/chrome/i.test(userAgent)) return 'Chrome';
  if (/node|undici|axios|okhttp/i.test(userAgent)) return 'Лаунчер';
  return userAgent.slice(0, 40);
}

const FAIL_REASONS = {
  wrong_code: 'Неверный код',
  no_active_code: 'Код просрочен',
  too_many_attempts: 'Слишком много попыток',
  user_not_found: 'Аккаунт не найден',
  banned: 'Аккаунт заблокирован',
};

export default function Security() {
  const { user, loadMe } = useAuth();
  const [sessions, setSessions] = useState([]);
  const [logs, setLogs] = useState([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const [sessionsData, logsData] = await Promise.all([
        api('/api/website/me/sessions'),
        api('/api/website/me/login-logs'),
      ]);
      setSessions(sessionsData.sessions);
      setLogs(logsData.logs);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const revoke = async (session) => {
    if (!window.confirm('Завершить эту сессию?')) return;
    try {
      await api(`/api/website/me/sessions/${session.id}`, { method: 'DELETE' });
      await load();
    } catch (err) {
      setError(err.message);
    }
  };

  const unbind = async () => {
    if (!window.confirm('Отвязать устройство? Следующий вход в лаунчер привяжет новое.')) return;
    setBusy(true);
    setError('');
    try {
      await api('/api/website/me', { method: 'PATCH', body: { hwid: null } });
      await loadMe();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="max-w-5xl mx-auto space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Защита</h1>
        <p className="text-sm text-muted mt-1">
          Устройство, активные сессии и история входов
        </p>
      </div>

      {error && (
        <div className="text-sm text-danger border border-danger/30 bg-danger/10 rounded-lg px-3 py-2">
          {error}
        </div>
      )}

      <div className="card p-5 flex flex-wrap items-center justify-between gap-3">
        <div>
          <div className="text-sm text-muted">Привязанное устройство</div>
          <div className="font-semibold mt-1">
            {user?.hwid_bound ? user.hwid_masked : 'Устройство не привязано'}
          </div>
          <div className="text-xs text-muted mt-1">
            {user?.hwid_bound
              ? 'Вход в лаунчер разрешён только с этого устройства'
              : 'Привяжется автоматически при первом входе в лаунчер'}
          </div>
        </div>
        {user?.hwid_bound && (
          <button type="button" className="btn btn-danger text-sm" onClick={unbind} disabled={busy}>
            Отвязать устройство
          </button>
        )}
      </div>

      <div className="card p-5">
        <div className="font-semibold mb-3">Активные сессии</div>
        {loading ? (
          <div className="text-sm text-muted">Загрузка…</div>
        ) : sessions.length === 0 ? (
          <div className="text-sm text-muted">Активных сессий нет</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="text-muted text-left">
                <tr>
                  <th className="py-2 pr-4 font-medium">Клиент</th>
                  <th className="py-2 pr-4 font-medium">IP</th>
                  <th className="py-2 pr-4 font-medium">Устройство</th>
                  <th className="py-2 pr-4 font-medium">Создана</th>
                  <th className="py-2 pr-4 font-medium">Истекает</th>
                  <th className="py-2 font-medium" />
                </tr>
              </thead>
              <tbody>
                {sessions.map((session) => (
                  <tr key={session.id} className="border-t border-line">
                    <td className="py-2 pr-4">
                      <span className={session.client_type === 'launcher' ? 'chip chip-warn' : 'chip'}>
                        {session.client_type === 'launcher' ? 'Лаунчер' : 'Сайт'}
                      </span>
                    </td>
                    <td className="py-2 pr-4">{session.ip ?? '—'}</td>
                    <td className="py-2 pr-4">{shortAgent(session.user_agent)}</td>
                    <td className="py-2 pr-4">{formatDate(session.created_at)}</td>
                    <td className="py-2 pr-4">{formatDate(session.expires_at)}</td>
                    <td className="py-2 text-right">
                      <button type="button" className="btn btn-danger text-xs" onClick={() => revoke(session)}>
                        Завершить
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <div className="card p-5">
        <div className="font-semibold mb-3">История входов</div>
        {loading ? (
          <div className="text-sm text-muted">Загрузка…</div>
        ) : logs.length === 0 ? (
          <div className="text-sm text-muted">Пока пусто</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="text-muted text-left">
                <tr>
                  <th className="py-2 pr-4 font-medium">Когда</th>
                  <th className="py-2 pr-4 font-medium">IP</th>
                  <th className="py-2 pr-4 font-medium">Устройство</th>
                  <th className="py-2 pr-4 font-medium">Результат</th>
                </tr>
              </thead>
              <tbody>
                {logs.map((log, index) => (
                  <tr key={index} className="border-t border-line">
                    <td className="py-2 pr-4">{formatDate(log.created_at)}</td>
                    <td className="py-2 pr-4">{log.ip ?? '—'}</td>
                    <td className="py-2 pr-4">{shortAgent(log.device)}</td>
                    <td className="py-2 pr-4">
                      {log.success ? (
                        <span className="chip chip-ok">Успешно</span>
                      ) : (
                        <span className="chip chip-danger">
                          {FAIL_REASONS[log.reason] ?? log.reason ?? 'Ошибка'}
                        </span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
