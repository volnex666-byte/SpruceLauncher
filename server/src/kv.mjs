import Redis from 'ioredis';
import { config } from './config.mjs';

function memoryStore() {
  const map = new Map();
  const timer = setInterval(() => {
    const now = Date.now();
    for (const [key, entry] of map) {
      if (entry.exp && entry.exp <= now) map.delete(key);
    }
  }, 30_000);
  timer.unref();

  const read = (key) => {
    const entry = map.get(key);
    if (!entry) return null;
    if (entry.exp && entry.exp <= Date.now()) {
      map.delete(key);
      return null;
    }
    return entry.value;
  };

  return {
    kind: 'memory',
    async get(key) {
      return read(key);
    },
    async set(key, value, ttlSec) {
      map.set(key, { value: String(value), exp: ttlSec ? Date.now() + ttlSec * 1000 : 0 });
    },
    async setIfAbsent(key, value, ttlSec) {
      if (read(key) !== null) return false;
      map.set(key, { value: String(value), exp: ttlSec ? Date.now() + ttlSec * 1000 : 0 });
      return true;
    },
    async del(key) {
      map.delete(key);
    },
    async incr(key, ttlSec) {
      const next = (Number.parseInt(read(key) ?? '0', 10) || 0) + 1;
      const existing = map.get(key);
      const exp = existing && existing.exp ? existing.exp : (ttlSec ? Date.now() + ttlSec * 1000 : 0);
      map.set(key, { value: String(next), exp });
      return next;
    },
    async close() {},
  };
}

function redisStore(url) {
  const client = new Redis(url, { maxRetriesPerRequest: 2 });
  return {
    kind: 'redis',
    async get(key) {
      return client.get(key);
    },
    async set(key, value, ttlSec) {
      if (ttlSec) await client.set(key, String(value), 'EX', ttlSec);
      else await client.set(key, String(value));
    },
    async setIfAbsent(key, value, ttlSec) {
      const result = ttlSec
        ? await client.set(key, String(value), 'EX', ttlSec, 'NX')
        : await client.set(key, String(value), 'NX');
      return result === 'OK';
    },
    async del(key) {
      await client.del(key);
    },
    async incr(key, ttlSec) {
      const count = await client.incr(key);
      if (count === 1 && ttlSec) await client.expire(key, ttlSec);
      return count;
    },
    async close() {
      await client.quit();
    },
  };
}

let store = null;

export function initKv() {
  store = config.redis.url ? redisStore(config.redis.url) : memoryStore();
  return store;
}

export function kv() {
  if (!store) throw new Error('KV store is not initialised');
  return store;
}
