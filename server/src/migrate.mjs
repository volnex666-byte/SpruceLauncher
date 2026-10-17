import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { db } from './db.mjs';

const migrationsDir = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  '..',
  'db',
  'migrations',
);

export async function runMigrations() {
  const database = db();
  await database.query(
    `CREATE TABLE IF NOT EXISTS schema_migrations (
       version TEXT PRIMARY KEY,
       applied_at TIMESTAMPTZ NOT NULL DEFAULT now()
     )`,
  );
  const applied = new Set(
    (await database.query('SELECT version FROM schema_migrations')).map((row) => row.version),
  );
  const files = (await fs.readdir(migrationsDir)).filter((f) => f.endsWith('.sql')).sort();

  const executed = [];
  for (const file of files) {
    if (applied.has(file)) continue;
    const sql = await fs.readFile(path.join(migrationsDir, file), 'utf8');
    const version = file.replace(/'/g, "''");
    const script = [
      'BEGIN;',
      sql,
      `INSERT INTO schema_migrations (version) VALUES ('${version}');`,
      'COMMIT;',
    ].join('\n');
    await database.exec(script);
    executed.push(file);
  }
  return executed;
}
