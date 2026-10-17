import { Link } from 'react-router-dom';
import { useAuth } from '../auth.jsx';

const FEATURES = [
  {
    title: 'Профили в облаке',
    text: 'Настраивай сборки на сайте — лаунчер подтянет их сам при входе. Ничего не потеряется при переустановке.',
  },
  {
    title: 'Защита аккаунта',
    text: 'Вход по одноразовому коду, привязка устройства и журнал входов — всегда видно, кто и откуда заходил.',
  },
  {
    title: 'Честные тарифы',
    text: 'Обычный — всё нужное за 50 ₽/мес. Креатор — создание тем и больше профилей.',
  },
  {
    title: 'Быстрый запуск',
    text: 'Тонкий клиент без лишнего: только твои профили, версии и моментальный старт игры.',
  },
];

const STEPS = [
  { n: '01', title: 'Создай аккаунт', text: 'По email за 30 секунд — без паролей, только код из письма.' },
  { n: '02', title: 'Настрой профили', text: 'Версия, загрузчик, моды и RAM — всё в личном кабинете.' },
  { n: '03', title: 'Запусти игру', text: 'Войди в лаунчер — профили появятся автоматически.' },
];

const PLANS_PREVIEW = [
  {
    title: 'Обычный',
    price: '50 ₽/мес',
    features: ['Обычные функции лаунчера', '1 профиль', '2048 МБ RAM'],
  },
  {
    title: 'Креатор',
    price: '320 ₽/мес',
    features: ['Создатель тем и т.д.', 'До 10 профилей', '8192 МБ RAM'],
    highlight: true,
  },
];

export default function Landing() {
  const { user } = useAuth();

  return (
    <div className="min-h-full flex flex-col">
      <header className="sticky top-0 z-40 border-b border-line bg-bg/85 backdrop-blur">
        <div className="max-w-6xl mx-auto px-4 h-16 flex items-center justify-between gap-4">
          <Link to="/" className="flex items-center gap-2 shrink-0">
            <span className="text-accent text-xl font-black leading-none">Spruce</span>
            <span className="font-semibold">Launcher</span>
          </Link>
          <nav className="hidden md:flex items-center gap-6 text-sm text-muted">
            <a href="#features" className="hover:text-white transition-colors">Возможности</a>
            <a href="#start" className="hover:text-white transition-colors">Как начать</a>
            <a href="#plans" className="hover:text-white transition-colors">Тарифы</a>
          </nav>
          <div className="flex items-center gap-2 shrink-0">
            {user ? (
              <Link to="/app" className="btn btn-primary text-sm">Личный кабинет</Link>
            ) : (
              <>
                <Link to="/login" className="btn btn-ghost text-sm">Войти</Link>
                <Link to="/login" state={{ mode: 'register' }} className="btn btn-primary text-sm">
                  Создать аккаунт
                </Link>
              </>
            )}
          </div>
        </div>
      </header>

      <section className="relative overflow-hidden">
        <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_50%_-20%,rgba(57,211,83,0.22),transparent_55%)]" />
        <div className="relative max-w-6xl mx-auto px-4 pt-20 pb-24 md:pt-28 md:pb-32 text-center">
          <div className="inline-flex">
            <span className="chip chip-ok">Minecraft · твои правила</span>
          </div>
          <h1 className="mt-6 text-5xl md:text-7xl font-black leading-[1.05] tracking-tight">
            ДОБРО ПОЖАЛОВАТЬ
            <span className="block bg-gradient-to-r from-accent to-[#9fe8ae] bg-clip-text text-transparent">
              в SpruceLauncher
            </span>
          </h1>
          <p className="mt-6 text-lg text-muted max-w-2xl mx-auto">
            Личный кабинет и лаунчер в одном: настрой сборки на сайте, войди по коду —
            и играй. Без лишних кнопок и непонятных настроек.
          </p>
          <div className="mt-10 flex flex-wrap justify-center gap-3">
            {user ? (
              <Link to="/app" className="btn btn-primary px-8 py-3 text-base">
                Открыть кабинет
              </Link>
            ) : (
              <>
                <Link to="/login" state={{ mode: 'register' }} className="btn btn-primary px-8 py-3 text-base">
                  Создать аккаунт
                </Link>
                <Link to="/login" className="btn btn-ghost px-8 py-3 text-base">
                  Войти
                </Link>
              </>
            )}
          </div>
        </div>
      </section>

      <section id="features" className="max-w-6xl mx-auto px-4 pb-20 w-full">
        <h2 className="text-3xl font-bold mb-2">Возможности</h2>
        <p className="text-muted mb-8">Всё, что нужно для комфортной игры — под рукой</p>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {FEATURES.map((feature) => (
            <div key={feature.title} className="card p-5">
              <div className="font-semibold mb-2">{feature.title}</div>
              <div className="text-sm text-muted">{feature.text}</div>
            </div>
          ))}
        </div>
      </section>

      <section id="start" className="max-w-6xl mx-auto px-4 pb-20 w-full">
        <h2 className="text-3xl font-bold mb-2">Как начать</h2>
        <p className="text-muted mb-8">Три шага до первого запуска</p>
        <div className="grid gap-4 md:grid-cols-3">
          {STEPS.map((step) => (
            <div key={step.n} className="card p-6">
              <div className="text-accent font-black text-2xl mb-3">{step.n}</div>
              <div className="font-semibold mb-2">{step.title}</div>
              <div className="text-sm text-muted">{step.text}</div>
            </div>
          ))}
        </div>
      </section>

      <section id="plans" className="max-w-6xl mx-auto px-4 pb-20 w-full">
        <h2 className="text-3xl font-bold mb-2">Тарифы</h2>
        <p className="text-muted mb-8">Два простых тарифа — выбирай под себя</p>
        <div className="grid gap-4 md:grid-cols-2 max-w-3xl">
          {PLANS_PREVIEW.map((plan) => (
            <div
              key={plan.title}
              className={`card p-6 ${plan.highlight ? 'border-accent/50' : ''}`}
            >
              <div className="flex items-start justify-between gap-2">
                <div className="text-xl font-bold">{plan.title}</div>
                {plan.highlight && <span className="chip chip-ok">Популярный</span>}
              </div>
              <div className="text-accent text-lg font-bold mt-1">{plan.price}</div>
              <ul className="text-sm text-muted space-y-1 mt-4">
                {plan.features.map((feature) => (
                  <li key={feature}>• {feature}</li>
                ))}
              </ul>
            </div>
          ))}
        </div>
      </section>

      <section className="max-w-6xl mx-auto px-4 pb-20 w-full">
        <div className="card p-8 md:p-10 text-center bg-gradient-to-b from-panel-2 to-panel">
          <div className="text-2xl md:text-3xl font-bold mb-3">Готов начать?</div>
          <p className="text-muted mb-6">Аккаунт создаётся за минуту — по одному коду из письма</p>
          {user ? (
            <Link to="/app" className="btn btn-primary px-8 py-3 text-base">
              Перейти в кабинет
            </Link>
          ) : (
            <Link to="/login" state={{ mode: 'register' }} className="btn btn-primary px-8 py-3 text-base">
              Создать аккаунт
            </Link>
          )}
        </div>
      </section>

      <footer className="mt-auto border-t border-line">
        <div className="max-w-6xl mx-auto px-4 py-6 flex flex-wrap items-center justify-between gap-3 text-sm text-muted">
          <div className="flex items-center gap-2">
            <span className="text-accent font-black">Spruce</span>
            <span>Launcher</span>
            <span className="text-xs">© 2026</span>
          </div>
          <div className="flex items-center gap-4">
            <Link to="/login" className="hover:text-white transition-colors">Вход</Link>
            <Link to="/login" state={{ mode: 'register' }} className="hover:text-white transition-colors">
              Регистрация
            </Link>
          </div>
        </div>
      </footer>
    </div>
  );
}
