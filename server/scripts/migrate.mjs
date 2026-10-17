import { initDb, db } from '../src/db.mjs';
import { runMigrations } from '../src/migrate.mjs';

try {
  await initDb();
  const applied = await runMigrations();
  if (applied.length) {
    console.log(`Applied migrations: ${applied.join(', ')}`);
  } else {
    console.log('Nothing to apply — schema is up to date');
  }
  await db().close();
  process.exit(0);
} catch (err) {
  console.error('Migration failed:', err);
  process.exit(1);
}
