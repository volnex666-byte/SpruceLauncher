import pg from 'pg';

let url = process.env.DATABASE_URL?.trim();
if (!url) {
  console.error('Укажи DATABASE_URL, например:');
  console.error('  $env:DATABASE_URL = "postgresql://..."');
  console.error('  node scripts/check-db.mjs');
  process.exit(1);
}
if (!url.includes('sslmode')) {
  url += (url.includes('?') ? '&' : '?') + 'sslmode=require';
}

const pool = new pg.Pool({ connectionString: url, max: 1, connectionTimeoutMillis: 15000 });
try {
  const result = await pool.query(
    'select current_database() as db, current_user as usr, version() as ver',
  );
  const row = result.rows[0];
  console.log(`[ok] Подключение успешно: база=${row.db}, пользователь=${row.usr}`);
  console.log(`[ok] ${String(row.ver).split(',')[0]}`);
} catch (err) {
  console.error('[fail] Не удалось подключиться:', err.message);
  process.exitCode = 1;
} finally {
  await pool.end();
}
