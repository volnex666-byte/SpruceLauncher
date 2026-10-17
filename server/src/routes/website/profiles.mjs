import { Router } from 'express';
import { z } from 'zod';
import { db } from '../../db.mjs';
import { ah, parse } from '../../http.mjs';
import { conflict, forbidden, notFound } from '../../errors.mjs';
import { PLANS } from '../../plans.mjs';
import { getEffectiveSubscription } from '../../subscriptions.mjs';
import { requireScope } from '../../middleware/auth.mjs';

const router = Router();

const modSchema = z.object({
  name: z.string().trim().min(1).max(128),
  url: z.string().max(2048).refine((u) => /^https?:\/\//i.test(u), 'Только http(s) ссылки').optional(),
  sha1: z.string().regex(/^[a-f0-9]{40}$/i, 'sha1 — 40 hex-символов').optional(),
  size: z.number().int().min(0).max(2_000_000_000).optional(),
});

const profileSchema = z.object({
  name: z.string().trim().min(1).max(48),
  mc_version: z.string().trim().regex(/^\d{1,2}(\.\d{1,2}){1,2}$/, 'Формат версии: 1.21.4'),
  loader: z.enum(['vanilla', 'fabric', 'forge', 'neoforge', 'quilt']).default('vanilla'),
  mods: z.array(modSchema).max(200).default([]),
  ram_mb: z.number().int().min(512).max(65536).default(4096),
  java_path: z.string().max(512).default(''),
  jvm_args: z.string().max(1000).default(''),
  is_default: z.boolean().default(false),
});

const profilePatchSchema = profileSchema.partial();

function serializeProfile(row) {
  return {
    id: row.id,
    name: row.name,
    mc_version: row.mc_version,
    loader: row.loader,
    mods: row.mods_json ?? [],
    ram_mb: row.ram_mb,
    java_path: row.java_path,
    jvm_args: row.jvm_args,
    is_default: row.is_default,
    created_at: row.created_at,
    updated_at: row.updated_at,
  };
}

async function currentPlan(userId) {
  const sub = await getEffectiveSubscription(userId);
  if (sub && sub.effectiveStatus === 'active' && !sub.expired) return sub.plan;
  return PLANS.free;
}

function assertRam(plan, ramMb) {
  if (ramMb > plan.maxRamMb) {
    throw forbidden(
      'plan_limit_ram',
      `Максимум ${plan.maxRamMb} МБ RAM для плана ${plan.title}. Увеличьте план, чтобы поднять лимит.`,
    );
  }
}

async function assertProfileLimit(userId, plan, excludeId = null) {
  const rows = await db().query(
    'SELECT count(*)::int AS c FROM profiles WHERE user_id = $1 AND ($2::uuid IS NULL OR id <> $2::uuid)',
    [userId, excludeId],
  );
  if (rows[0].c >= plan.maxProfiles) {
    throw forbidden(
      'plan_limit_profiles',
      `Лимит профилей плана ${plan.title}: ${plan.maxProfiles}. Удалите лишний профиль или смените план.`,
    );
  }
}

function mapDbError(err) {
  if (err?.code === '23505') {
    throw conflict('name_taken', 'Профиль с таким именем уже существует');
  }
  throw err;
}

async function findProfile(userId, id) {
  const rows = await db().query('SELECT * FROM profiles WHERE id = $1 AND user_id = $2', [id, userId]);
  if (!rows.length) throw notFound('profile_not_found', 'Профиль не найден');
  return rows[0];
}

router.get('/', ah(async (req, res) => {
  const rows = await db().query(
    `SELECT * FROM profiles WHERE user_id = $1
     ORDER BY is_default DESC, created_at ASC`,
    [req.user.id],
  );
  const plan = await currentPlan(req.user.id);
  res.json({
    profiles: rows.map(serializeProfile),
    limits: { max_profiles: plan.maxProfiles, max_ram_mb: plan.maxRamMb },
  });
}));

router.get('/:id', ah(async (req, res) => {
  const profile = await findProfile(req.user.id, req.params.id);
  res.json({ profile: serializeProfile(profile) });
}));

router.post('/', requireScope('website:write'), ah(async (req, res) => {
  const body = parse(profileSchema, req.body);
  const plan = await currentPlan(req.user.id);
  await assertProfileLimit(req.user.id, plan);
  assertRam(plan, body.ram_mb);

  try {
    const profile = await db().tx(async (tx) => {
      const countRows = await tx.query(
        'SELECT count(*)::int AS c FROM profiles WHERE user_id = $1',
        [req.user.id],
      );
      const makeDefault = body.is_default || countRows[0].c === 0;
      if (makeDefault) {
        await tx.query('UPDATE profiles SET is_default = false WHERE user_id = $1', [req.user.id]);
      }
      const rows = await tx.query(
        `INSERT INTO profiles (user_id, name, mc_version, loader, mods_json, ram_mb, java_path, jvm_args, is_default)
         VALUES ($1, $2, $3, $4, $5::jsonb, $6, $7, $8, $9)
         RETURNING *`,
        [
          req.user.id,
          body.name,
          body.mc_version,
          body.loader,
          JSON.stringify(body.mods),
          body.ram_mb,
          body.java_path,
          body.jvm_args,
          makeDefault,
        ],
      );
      return rows[0];
    });
    res.status(201).json({ profile: serializeProfile(profile) });
  } catch (err) {
    mapDbError(err);
  }
}));

router.patch('/:id', requireScope('website:write'), ah(async (req, res) => {
  const existing = await findProfile(req.user.id, req.params.id);
  const body = parse(profilePatchSchema, req.body);
  const plan = await currentPlan(req.user.id);

  const ramMb = body.ram_mb ?? existing.ram_mb;
  assertRam(plan, ramMb);

  const merged = {
    name: body.name ?? existing.name,
    mc_version: body.mc_version ?? existing.mc_version,
    loader: body.loader ?? existing.loader,
    mods: body.mods ?? existing.mods_json ?? [],
    ram_mb: ramMb,
    java_path: body.java_path ?? existing.java_path,
    jvm_args: body.jvm_args ?? existing.jvm_args,
    is_default: body.is_default ?? existing.is_default,
  };

  try {
    const profile = await db().tx(async (tx) => {
      if (merged.is_default) {
        await tx.query(
          'UPDATE profiles SET is_default = false WHERE user_id = $1 AND id <> $2',
          [req.user.id, existing.id],
        );
      }
      const rows = await tx.query(
        `UPDATE profiles
         SET name = $1, mc_version = $2, loader = $3, mods_json = $4::jsonb,
             ram_mb = $5, java_path = $6, jvm_args = $7, is_default = $8
         WHERE id = $9 AND user_id = $10
         RETURNING *`,
        [
          merged.name,
          merged.mc_version,
          merged.loader,
          JSON.stringify(merged.mods),
          merged.ram_mb,
          merged.java_path,
          merged.jvm_args,
          merged.is_default,
          existing.id,
          req.user.id,
        ],
      );
      return rows[0];
    });
    res.json({ profile: serializeProfile(profile) });
  } catch (err) {
    mapDbError(err);
  }
}));

router.post('/:id/default', requireScope('website:write'), ah(async (req, res) => {
  const existing = await findProfile(req.user.id, req.params.id);
  await db().tx(async (tx) => {
    await tx.query('UPDATE profiles SET is_default = false WHERE user_id = $1', [req.user.id]);
    await tx.query('UPDATE profiles SET is_default = true WHERE id = $1', [existing.id]);
  });
  res.json({ ok: true, id: existing.id });
}));

router.delete('/:id', requireScope('website:write'), ah(async (req, res) => {
  const existing = await findProfile(req.user.id, req.params.id);
  await db().tx(async (tx) => {
    await tx.query('DELETE FROM profiles WHERE id = $1 AND user_id = $2', [existing.id, req.user.id]);
    if (existing.is_default) {
      await tx.query(
        `UPDATE profiles SET is_default = true
         WHERE id = (SELECT id FROM profiles WHERE user_id = $1 ORDER BY created_at ASC LIMIT 1)`,
        [req.user.id],
      );
    }
  });
  res.json({ ok: true });
}));

export default router;
