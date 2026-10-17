import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api.js';
import { useAuth, uiFlags } from '../auth.jsx';

function StatCard({ title, value, hint, badge }) {
  return (
    <div className="card p-5">
      <div className="text-sm text-muted mb-2">{title}</div>
      <div className="text-2xl font-bold mb-1">{value}</div>
      {badge}
      {hint && <div className="text-xs text-muted mt-1">{hint}</div>}
    </div>
  );
}

export default function Overview() {
  const { user, subscription, setSubscription } = useAuth();
  const [me, setMe] = useState(null);
  const [error, setError] = useState('');
  const [welcome, setWelcome] = useState(uiFlags.welcome);

  useEffect(() => {
    if (!uiFlags.welcome) return undefined;
    const timer = setTimeout(() => {
      uiFlags.welcome = false;
      setWelcome(false);
    }, 20000);
    return () => clearTimeout(timer);
  }, []);

  const hideWelcome = () => {
    uiFlags.welcome = false;
    setWelcome(false);
  };

  useEffect(() => {
    let alive = true;
    api('/api/website/me')
      .then((data) => {
        if (!alive) return;
        setMe(data);
        setSubscription(data.subscription);
      })
      .catch((err) => alive && setError(err.message));
    return () => {
      alive = false;
    };
  }, [setSubscription]);

  const sub = me?.subscription ?? subscription;
  const expiresText = !sub
    ? ''
    : sub.expires_at
      ? `до ${new Date(sub.expires_at).toLocaleDateString('ru-RU')} · осталось ${sub.days_left} дн.`
      : 'бессрочно';

  return (
    <div className="max-w-5xl mx-auto space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Обзор</h1>
        <p className="text-sm text-muted mt-1">Состояние аккаунта SpruceLauncher</p>
      </div>

      {welcome && (
        <div className="flex items-center justify-between gap-3 text-sm text-accent border border-accent/30 bg-accent/10 rounded-lg px-3 py-2">
          <span>Аккаунт создан — добро пожаловать в SpruceLauncher!</span>
          <button type="button" className="text-muted hover:text-white" onClick={hideWelcome}>
            Скрыть
          </button>
        </div>
      )}

      {error && (
        <div className="text-sm text-danger border border-danger/30 bg-danger/10 rounded-lg px-3 py-2">
          {error}
        </div>
      )}

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard
          title="Подписка"
          value={sub?.plan_title ?? '—'}
          badge={
            sub && (
              <span className={sub.can_play ? 'chip chip-ok' : 'chip chip-danger'}>
                {sub.can_play ? 'Играть можно' : 'Играть нельзя'}
              </span>
            )
          }
          hint={expiresText}
        />
        <StatCard
          title="Профили"
          value={me ? `${me.profiles_count} / ${sub?.limits?.max_profiles ?? '—'}` : '…'}
          hint={sub ? `Максимум RAM: ${sub.limits.max_ram_mb} МБ` : ''}
        />
        <StatCard
          title="Устройство"
          value={user?.hwid_bound ? 'Привязано' : 'Не привязано'}
          hint={user?.hwid_masked ?? 'Привяжется при первом входе в лаунчер'}
        />
        <StatCard
          title="Аккаунт"
          value={user?.email ?? '…'}
          hint={user?.created_at ? `С нами с ${new Date(user.created_at).toLocaleDateString('ru-RU')}` : ''}
        />
      </div>

      <div className="card p-5">
        <div className="font-semibold mb-3">Быстрые действия</div>
        <div className="flex flex-wrap gap-2">
          <Link className="btn btn-primary" to="/app/profiles">
            Мои профили
          </Link>
          <Link className="btn btn-ghost" to="/app/subscription">
            Управлять подпиской
          </Link>
          <Link className="btn btn-ghost" to="/app/security">
            Безопасность и сессии
          </Link>
        </div>
        <p className="text-xs text-muted mt-4">
          Профили создаются и меняются только здесь, на сайте. Лаунчер получает их автоматически при входе.
        </p>
      </div>
    </div>
  );
}
