import { useEffect, useRef, useState } from 'react';
import { Navigate, Link, useLocation, useNavigate } from 'react-router-dom';
import { requestCode, verifyCode, ApiError } from '../api.js';
import { useAuth, uiFlags } from '../auth.jsx';

const CODE_LENGTH = 6;

export default function Login() {
  const { user, setSession } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [mode, setMode] = useState(location.state?.mode === 'register' ? 'register' : 'login');
  const [step, setStep] = useState('email');
  const [email, setEmail] = useState('');
  const [code, setCode] = useState('');
  const [devCode, setDevCode] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [cooldown, setCooldown] = useState(0);
  const timerRef = useRef(null);

  useEffect(() => () => clearInterval(timerRef.current), []);

  if (user) return <Navigate to="/app" replace />;

  const startCooldown = () => {
    clearInterval(timerRef.current);
    setCooldown(60);
    timerRef.current = setInterval(() => {
      setCooldown((value) => {
        if (value <= 1) {
          clearInterval(timerRef.current);
          return 0;
        }
        return value - 1;
      });
    }, 1000);
  };

  const errorText = (err, fallback) => (err instanceof ApiError ? err.message : fallback);

  const switchMode = (nextMode) => {
    setMode(nextMode);
    setStep('email');
    setError('');
  };

  const submitEmail = async (event) => {
    event.preventDefault();
    setBusy(true);
    setError('');
    try {
      const data = await requestCode(email.trim());
      setDevCode(data?.dev_code ?? '');
      setStep('code');
      setCode('');
      startCooldown();
    } catch (err) {
      setError(errorText(err, 'Не удалось отправить код'));
    } finally {
      setBusy(false);
    }
  };

  const submitCode = async (event) => {
    event.preventDefault();
    setBusy(true);
    setError('');
    try {
      const data = await verifyCode(email.trim(), code.trim());
      if (data.is_new_user) uiFlags.welcome = true;
      setSession(data);
      navigate('/app', { replace: true });
    } catch (err) {
      setError(errorText(err, 'Не удалось войти'));
    } finally {
      setBusy(false);
    }
  };

  const resend = async () => {
    if (cooldown > 0 || busy) return;
    setError('');
    try {
      const data = await requestCode(email.trim());
      setDevCode(data?.dev_code ?? '');
      startCooldown();
    } catch (err) {
      setError(errorText(err, 'Не удалось отправить код'));
    }
  };

  const isRegister = mode === 'register';

  return (
    <div className="min-h-full flex items-center justify-center p-4">
      <div className="card w-full max-w-md p-6">
        <Link to="/" className="text-xs text-muted hover:text-white transition-colors">
          ← На главную
        </Link>
        <div className="flex items-center gap-2 mb-1 mt-3">
          <span className="text-accent text-2xl font-black leading-none">Spruce</span>
          <span className="font-semibold">Launcher</span>
        </div>
        <p className="text-sm text-muted mb-5">
          {isRegister
            ? 'Регистрация — создадим аккаунт и пришлём код на почту'
            : 'Личный кабинет — вход по коду из письма'}
        </p>

        <div className="grid grid-cols-2 gap-1 p-1 rounded-xl border border-line bg-bg mb-5">
          <button
            type="button"
            className={`rounded-lg py-2 text-sm font-semibold transition-colors ${
              !isRegister ? 'bg-accent/15 text-accent' : 'text-muted hover:text-white'
            }`}
            onClick={() => switchMode('login')}
          >
            Вход
          </button>
          <button
            type="button"
            className={`rounded-lg py-2 text-sm font-semibold transition-colors ${
              isRegister ? 'bg-accent/15 text-accent' : 'text-muted hover:text-white'
            }`}
            onClick={() => switchMode('register')}
          >
            Регистрация
          </button>
        </div>

        {step === 'email' ? (
          <form onSubmit={submitEmail} className="space-y-4">
            <label className="block">
              <span className="text-sm text-muted mb-1 block">Email</span>
              <input
                type="email"
                required
                autoFocus
                className="input"
                placeholder="you@example.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
              />
            </label>
            <button type="submit" className="btn btn-primary w-full" disabled={busy || !email.trim()}>
              {busy ? 'Создаём…' : isRegister ? 'Создать аккаунт' : 'Получить код'}
            </button>
            <p className="text-xs text-muted">
              {isRegister
                ? 'Если аккаунт с такой почтой уже есть — просто войдёте в него.'
                : 'Пароль не нужен: одноразовый код действует 10 минут.'}
            </p>
          </form>
        ) : (
          <form onSubmit={submitCode} className="space-y-4">
            <label className="block">
              <span className="text-sm text-muted mb-1 block">
                Код из письма для <span className="text-white">{email}</span>
              </span>
              <input
                inputMode="numeric"
                autoComplete="one-time-code"
                required
                autoFocus
                className="input tracking-[0.5em] text-center text-lg font-semibold"
                placeholder="••••••"
                maxLength={CODE_LENGTH}
                value={code}
                onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, CODE_LENGTH))}
              />
            </label>

            {devCode && (
              <div className="text-sm rounded-lg px-3 py-2 border border-warn/40 bg-warn/10">
                <span className="text-muted">Почта ещё не подключена (dev-режим). Ваш код: </span>
                <span className="font-mono font-bold text-warn tracking-[0.3em]">{devCode}</span>
              </div>
            )}

            <button
              type="submit"
              className="btn btn-primary w-full"
              disabled={busy || code.length !== CODE_LENGTH}
            >
              {busy ? 'Проверяем…' : isRegister ? 'Подтвердить и создать' : 'Войти'}
            </button>
            <div className="flex items-center justify-between text-sm">
              <button type="button" className="text-muted hover:text-white" onClick={() => setStep('email')}>
                Изменить email
              </button>
              <button
                type="button"
                className="text-muted hover:text-white disabled:opacity-50"
                onClick={resend}
                disabled={cooldown > 0}
              >
                {cooldown > 0 ? `Ещё раз через ${cooldown} с` : 'Отправить код ещё раз'}
              </button>
            </div>
          </form>
        )}

        {error && (
          <div className="mt-4 text-sm text-danger border border-danger/30 bg-danger/10 rounded-lg px-3 py-2">
            {error}
          </div>
        )}
      </div>
    </div>
  );
}
