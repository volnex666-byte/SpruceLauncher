import { useCallback, useEffect, useState } from 'react';
import { api } from '../api.js';
import { useAuth } from '../auth.jsx';

const PLAN_CARDS = [
  {
    key: 'free',
    title: 'Обычный',
    price: '50 ₽/мес',
    features: ['Обычные функции лаунчера', '1 профиль', 'до 2048 МБ RAM'],
  },
  {
    key: 'premium',
    title: 'Креатор',
    price: '320 ₽/мес',
    features: ['Создатель тем и т.д.', 'до 10 профилей', 'до 8192 МБ RAM'],
    highlight: true,
  },
];

const PLAN_TITLES = { free: 'Обычный', premium: 'Креатор', legendary: 'Legendary' };

export default function Subscription() {
  const { setSubscription } = useAuth();
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const result = await api('/api/website/subscription');
      setData(result);
      setSubscription(result.subscription);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }, [setSubscription]);

  useEffect(() => {
    load();
  }, [load]);

  const cancel = async () => {
    if (!window.confirm('Отменить подписку? Доступ сохранится до конца оплаченного периода.')) return;
    setBusy(true);
    setError('');
    try {
      await api('/api/website/subscription/cancel', { method: 'POST', body: {} });
      await load();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  const sub = data?.subscription;
  const paid = sub && sub.plan_type !== 'free';
  const history = data?.history ?? [];
  const payments = data?.payments ?? [];

  return (
    <div className="max-w-5xl mx-auto space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Подписка</h1>
        <p className="text-sm text-muted mt-1">Тариф, срок действия и история</p>
      </div>

      {error && (
        <div className="text-sm text-danger border border-danger/30 bg-danger/10 rounded-lg px-3 py-2">
          {error}
        </div>
      )}

      {loading ? (
        <div className="text-muted">Загрузка…</div>
      ) : (
        <>
          <div className="card p-5">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <div className="text-sm text-muted">Текущий тариф</div>
                <div className="text-2xl font-bold mt-1">{sub?.plan_title ?? '—'}</div>
                <div className="text-sm text-muted mt-1">
                  {sub?.expires_at
                    ? `Действует до ${new Date(sub.expires_at).toLocaleDateString('ru-RU')} · осталось ${sub.days_left} дн.`
                    : 'Бессрочно'}
                </div>
              </div>
              <div className="flex items-center gap-3">
                <span className={sub?.can_play ? 'chip chip-ok' : 'chip chip-danger'}>
                  {sub?.can_play ? 'Играть можно' : 'Играть нельзя'}
                </span>
                {paid && (
                  <button type="button" className="btn btn-danger text-sm" onClick={cancel} disabled={busy}>
                    Отменить подписку
                  </button>
                )}
              </div>
            </div>
          </div>

          <div className="grid gap-4 md:grid-cols-2 max-w-3xl">
            {PLAN_CARDS.map((card) => {
              const current = sub?.plan_type === card.key;
              return (
                <div
                  key={card.key}
                  className={`card p-5 flex flex-col gap-3 ${
                    current ? 'border-accent/50' : card.highlight ? 'border-accent/25' : ''
                  }`}
                >
                  <div className="flex items-start justify-between gap-2">
                    <div className="text-xl font-bold">{card.title}</div>
                    {current && <span className="chip chip-ok">Текущий</span>}
                  </div>
                  <div className="text-accent text-lg font-bold">{card.price}</div>
                  <ul className="text-sm text-muted space-y-1">
                    {card.features.map((feature) => (
                      <li key={feature}>• {feature}</li>
                    ))}
                  </ul>
                  <button type="button" className="btn btn-ghost mt-auto" disabled>
                    Оплата появится позже
                  </button>
                </div>
              );
            })}
          </div>

          <div className="card p-5">
            <div className="font-semibold mb-3">История подписок</div>
            {history.length === 0 ? (
              <div className="text-sm text-muted">Пока пусто</div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead className="text-muted text-left">
                    <tr>
                      <th className="py-2 pr-4 font-medium">План</th>
                      <th className="py-2 pr-4 font-medium">Статус</th>
                      <th className="py-2 pr-4 font-medium">Начало</th>
                      <th className="py-2 pr-4 font-medium">Окончание</th>
                    </tr>
                  </thead>
                  <tbody>
                    {history.map((row) => (
                      <tr key={row.id} className="border-t border-line">
                        <td className="py-2 pr-4">{PLAN_TITLES[row.plan_type] ?? row.plan_type}</td>
                        <td className="py-2 pr-4">{row.status}</td>
                        <td className="py-2 pr-4">{new Date(row.started_at).toLocaleDateString('ru-RU')}</td>
                        <td className="py-2 pr-4">
                          {row.expires_at ? new Date(row.expires_at).toLocaleDateString('ru-RU') : 'бессрочно'}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          <div className="card p-5">
            <div className="font-semibold mb-3">Платежи</div>
            {payments.length === 0 ? (
              <div className="text-sm text-muted">
                Платежей пока нет. Онлайн-оплата появится в ближайших обновлениях.
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead className="text-muted text-left">
                    <tr>
                      <th className="py-2 pr-4 font-medium">Дата</th>
                      <th className="py-2 pr-4 font-medium">Сумма</th>
                      <th className="py-2 pr-4 font-medium">Статус</th>
                      <th className="py-2 pr-4 font-medium">Способ</th>
                    </tr>
                  </thead>
                  <tbody>
                    {payments.map((row) => (
                      <tr key={row.id} className="border-t border-line">
                        <td className="py-2 pr-4">{new Date(row.created_at).toLocaleString('ru-RU')}</td>
                        <td className="py-2 pr-4">
                          {(row.amount_cents / 100).toFixed(2)} {row.currency}
                        </td>
                        <td className="py-2 pr-4">{row.status}</td>
                        <td className="py-2 pr-4">{row.provider}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </>
      )}
    </div>
  );
}
