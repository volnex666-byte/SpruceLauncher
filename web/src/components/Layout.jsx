import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth.jsx';

const NAV_ITEMS = [
  { to: '/app', label: 'Обзор', end: true },
  { to: '/app/profiles', label: 'Профили' },
  { to: '/app/subscription', label: 'Подписка' },
  { to: '/app/security', label: 'Защита' },
];

function planChip(subscription) {
  if (!subscription) return null;
  const canPlay = subscription.can_play;
  const cls = canPlay ? 'chip chip-ok' : 'chip chip-danger';
  const text = canPlay
    ? `${subscription.plan_title} · активна`
    : `${subscription.plan_title} · неактивна`;
  return <span className={cls}>{text}</span>;
}

export default function Layout() {
  const { user, subscription, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = async () => {
    await logout();
    navigate('/login', { replace: true });
  };

  return (
    <div className="min-h-full flex flex-col lg:flex-row">
      <aside className="lg:w-64 shrink-0 border-b lg:border-b-0 lg:border-r border-line bg-panel/60 px-4 py-3 lg:py-6 flex lg:flex-col items-center lg:items-stretch gap-3 lg:gap-1">
        <div className="flex items-center gap-2 px-2 lg:pb-5">
          <NavLink to="/" className="flex items-center gap-2">
            <span className="text-accent text-xl font-black leading-none">Spruce</span>
            <span className="font-semibold text-sm">Launcher</span>
          </NavLink>
        </div>
        <nav className="flex lg:flex-col gap-1 flex-1 overflow-x-auto lg:overflow-visible">
          {NAV_ITEMS.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                `px-3 py-2 rounded-lg text-sm font-medium whitespace-nowrap transition-colors ${
                  isActive
                    ? 'bg-accent/10 text-accent'
                    : 'text-muted hover:text-white hover:bg-white/5'
                }`
              }
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="hidden lg:block px-3 pt-4 text-xs text-muted">
          Личный кабинет v0.1
        </div>
      </aside>

      <div className="flex-1 flex flex-col min-w-0">
        <header className="flex items-center justify-between gap-4 px-6 py-3 border-b border-line bg-panel/40">
          <div className="min-w-0">
            <div className="text-sm font-semibold truncate">{user?.email}</div>
            <div className="mt-1">{planChip(subscription)}</div>
          </div>
          <button type="button" className="btn btn-ghost text-sm shrink-0" onClick={handleLogout}>
            Выйти
          </button>
        </header>
        <main className="p-6 flex-1 min-w-0">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
