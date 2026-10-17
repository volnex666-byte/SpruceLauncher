import { useCallback, useEffect, useState } from 'react';
import { api, ApiError } from '../api.js';
import Modal from '../components/Modal.jsx';

const LOADERS = ['vanilla', 'fabric', 'forge', 'neoforge', 'quilt'];
const LOADER_TITLES = {
  vanilla: 'Vanilla',
  fabric: 'Fabric',
  forge: 'Forge',
  neoforge: 'NeoForge',
  quilt: 'Quilt',
};

const EMPTY_FORM = {
  name: '',
  mc_version: '1.21.4',
  loader: 'fabric',
  ram_mb: 2048,
  java_path: '',
  jvm_args: '',
  is_default: false,
};

function errorText(err, fallback) {
  if (err instanceof ApiError) {
    if (err.code === 'plan_limit_ram' || err.code === 'plan_limit_profiles') return err.message;
    if (err.details?.length) {
      return err.details.map((d) => `${d.path}: ${d.message}`).join('; ');
    }
    return err.message;
  }
  return fallback;
}

export default function Profiles() {
  const [profiles, setProfiles] = useState([]);
  const [limits, setLimits] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [formError, setFormError] = useState('');
  const [editing, setEditing] = useState(null); // null = закрыто, {} = создание, profile = правка
  const [form, setForm] = useState(EMPTY_FORM);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const data = await api('/api/website/profiles');
      setProfiles(data.profiles);
      setLimits(data.limits);
    } catch (err) {
      setError(errorText(err, 'Не удалось загрузить профили'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const openCreate = () => {
    setFormError('');
    setForm({ ...EMPTY_FORM, ram_mb: Math.min(2048, limits?.max_ram_mb ?? 2048) });
    setEditing({});
  };

  const openEdit = (profile) => {
    setFormError('');
    setForm({
      name: profile.name,
      mc_version: profile.mc_version,
      loader: profile.loader,
      ram_mb: profile.ram_mb,
      java_path: profile.java_path ?? '',
      jvm_args: profile.jvm_args ?? '',
      is_default: profile.is_default,
    });
    setEditing(profile);
  };

  const save = async (event) => {
    event.preventDefault();
    setBusy(true);
    setFormError('');
    const payload = {
      name: form.name.trim(),
      mc_version: form.mc_version.trim(),
      loader: form.loader,
      ram_mb: Number(form.ram_mb),
      java_path: form.java_path.trim(),
      jvm_args: form.jvm_args.trim(),
      is_default: Boolean(form.is_default),
    };
    try {
      if (editing?.id) {
        await api(`/api/website/profiles/${editing.id}`, { method: 'PATCH', body: payload });
      } else {
        await api('/api/website/profiles', { method: 'POST', body: payload });
      }
      setEditing(null);
      await load();
    } catch (err) {
      setFormError(errorText(err, 'Не удалось сохранить профиль'));
    } finally {
      setBusy(false);
    }
  };

  const remove = async (profile) => {
    if (!window.confirm(`Удалить профиль «${profile.name}»?`)) return;
    try {
      await api(`/api/website/profiles/${profile.id}`, { method: 'DELETE' });
      await load();
    } catch (err) {
      setError(errorText(err, 'Не удалось удалить профиль'));
    }
  };

  const makeDefault = async (profile) => {
    try {
      await api(`/api/website/profiles/${profile.id}/default`, { method: 'POST' });
      await load();
    } catch (err) {
      setError(errorText(err, 'Не удалось сменить профиль по умолчанию'));
    }
  };

  const atLimit = limits != null && profiles.length >= limits.max_profiles;

  return (
    <div className="max-w-5xl mx-auto space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold">Профили</h1>
          <p className="text-sm text-muted mt-1">
            {limits
              ? `Использовано ${profiles.length} из ${limits.max_profiles} · лимит RAM ${limits.max_ram_mb} МБ`
              : 'Загрузка…'}
          </p>
        </div>
        <button type="button" className="btn btn-primary" onClick={openCreate} disabled={atLimit}>
          Создать профиль
        </button>
      </div>

      {atLimit && (
        <div className="text-sm text-warn border border-warn/30 bg-warn/10 rounded-lg px-3 py-2">
          Достигнут лимит профилей вашего плана. Смените план на странице «Подписка», чтобы создавать больше.
        </div>
      )}

      {error && (
        <div className="text-sm text-danger border border-danger/30 bg-danger/10 rounded-lg px-3 py-2">
          {error}
        </div>
      )}

      {loading ? (
        <div className="text-muted">Загрузка…</div>
      ) : profiles.length === 0 ? (
        <div className="card p-8 text-center">
          <div className="font-semibold mb-1">Профилей пока нет</div>
          <p className="text-sm text-muted mb-4">
            Создайте первый профиль — лаунчер получит его автоматически.
          </p>
          <button type="button" className="btn btn-primary" onClick={openCreate}>
            Создать профиль
          </button>
        </div>
      ) : (
        <div className="grid gap-4 md:grid-cols-2">
          {profiles.map((profile) => (
            <div key={profile.id} className="card p-5 flex flex-col gap-3">
              <div className="flex items-center justify-between gap-2">
                <div className="font-semibold truncate">{profile.name}</div>
                {profile.is_default && <span className="chip chip-ok">По умолчанию</span>}
              </div>
              <div className="flex flex-wrap gap-2 text-xs">
                <span className="chip">{profile.mc_version}</span>
                <span className="chip">{LOADER_TITLES[profile.loader] ?? profile.loader}</span>
                <span className="chip">{profile.ram_mb} МБ RAM</span>
                <span className="chip">модов: {profile.mods?.length ?? 0}</span>
              </div>
              {profile.jvm_args ? (
                <div className="text-xs text-muted truncate" title={profile.jvm_args}>
                  JVM: {profile.jvm_args}
                </div>
              ) : null}
              <div className="flex flex-wrap gap-2 mt-auto pt-2">
                {!profile.is_default && (
                  <button type="button" className="btn btn-ghost text-sm" onClick={() => makeDefault(profile)}>
                    Сделать основным
                  </button>
                )}
                <button type="button" className="btn btn-ghost text-sm" onClick={() => openEdit(profile)}>
                  Изменить
                </button>
                <button type="button" className="btn btn-danger text-sm" onClick={() => remove(profile)}>
                  Удалить
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {editing && (
        <Modal
          title={editing.id ? 'Изменить профиль' : 'Новый профиль'}
          onClose={() => setEditing(null)}
          footer={
            <>
              <button type="button" className="btn btn-ghost" onClick={() => setEditing(null)}>
                Отмена
              </button>
              <button type="submit" form="profile-form" className="btn btn-primary" disabled={busy}>
                {busy ? 'Сохраняем…' : 'Сохранить'}
              </button>
            </>
          }
        >
          <form id="profile-form" onSubmit={save} className="space-y-4">
            <label className="block">
              <span className="text-sm text-muted mb-1 block">Название</span>
              <input
                className="input"
                required
                maxLength={48}
                value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
                placeholder="Мой выживач"
              />
            </label>

            <div className="grid grid-cols-2 gap-3">
              <label className="block">
                <span className="text-sm text-muted mb-1 block">Версия Minecraft</span>
                <input
                  className="input"
                  required
                  pattern="\d{1,2}(\.\d{1,2}){1,2}"
                  value={form.mc_version}
                  onChange={(e) => setForm({ ...form, mc_version: e.target.value })}
                  placeholder="1.21.4"
                />
              </label>
              <label className="block">
                <span className="text-sm text-muted mb-1 block">Загрузчик</span>
                <select
                  className="input"
                  value={form.loader}
                  onChange={(e) => setForm({ ...form, loader: e.target.value })}
                >
                  {LOADERS.map((loader) => (
                    <option key={loader} value={loader}>
                      {LOADER_TITLES[loader]}
                    </option>
                  ))}
                </select>
              </label>
            </div>

            <label className="block">
              <span className="text-sm text-muted mb-1 block">
                Оперативная память: {form.ram_mb} МБ
                {limits ? ` (макс. ${limits.max_ram_mb})` : ''}
              </span>
              <input
                type="range"
                min={512}
                max={limits?.max_ram_mb ?? 65536}
                step={512}
                value={form.ram_mb}
                onChange={(e) => setForm({ ...form, ram_mb: Number(e.target.value) })}
                className="w-full accent-[#39d353]"
              />
            </label>

            <label className="block">
              <span className="text-sm text-muted mb-1 block">Путь к Java (необязательно)</span>
              <input
                className="input"
                value={form.java_path}
                onChange={(e) => setForm({ ...form, java_path: e.target.value })}
                placeholder="C:\Java\jdk-21\bin\javaw.exe"
              />
            </label>

            <label className="block">
              <span className="text-sm text-muted mb-1 block">JVM-аргументы (необязательно)</span>
              <input
                className="input"
                value={form.jvm_args}
                onChange={(e) => setForm({ ...form, jvm_args: e.target.value })}
                placeholder="-XX:+UseG1GC"
              />
            </label>

            <label className="flex items-center gap-2 text-sm">
              <input
                type="checkbox"
                checked={form.is_default}
                onChange={(e) => setForm({ ...form, is_default: e.target.checked })}
              />
              Профиль по умолчанию
            </label>

            {formError && (
              <div className="text-sm text-danger border border-danger/30 bg-danger/10 rounded-lg px-3 py-2">
                {formError}
              </div>
            )}
          </form>
        </Modal>
      )}
    </div>
  );
}
