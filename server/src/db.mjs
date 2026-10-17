import fs from 'node:fs';
import path from 'node:path';
import pg from 'pg';
import { config } from './config.mjs';

let impl = null;

export async function initDb() {
  if (config.db.url) {
    const pool = new pg.Pool({ connectionString: config.db.url, max: 10 });
    await pool.query('SELECT 1');
    impl = {
      kind: 'postgres',
      query: (text, params = []) => pool.query(text, params).then((r) => r.rows),
      exec: (sql) => pool.query(sql),
      tx: async (fn) => {
        const client = await pool.connect();
        try {
          await client.query('BEGIN');
          const result = await fn({
            query: (text, params = []) => client.query(text, params).then((r) => r.rows),
          });
          await client.query('COMMIT');
          return result;
        } catch (err) {
          await client.query('ROLLBACK');
          throw err;
        } finally {
          client.release();
        }
      },
      close: () => pool.end(),
    };
    return;
  }

  if (!config.db.usePglite) {
    throw new Error('No DATABASE_URL set and PGLITE=0 — nothing to connect to');
  }
  const { PGlite } = await import('@electric-sql/pglite');
  const dataDir = path.resolve(config.db.pgliteDir);
  fs.mkdirSync(dataDir, { recursive: true });
  const pglite = new PGlite(dataDir);
  await pglite.waitReady;
  impl = {
    kind: 'pglite',
    query: (text, params = []) => pglite.query(text, params).then((r) => r.rows),
    exec: (sql) => pglite.exec(sql),
    tx: (fn) => pglite.transaction((tx) => fn({
      query: (text, params = []) => tx.query(text, params).then((r) => r.rows),
    })),
    close: () => pglite.close(),
  };
}

export function db() {
  if (!impl) throw new Error('Database is not initialised');
  return impl;
}
