import { Router } from 'express';
import { db } from '../../db.mjs';
import { ah } from '../../http.mjs';
import { getEffectiveSubscription } from '../../subscriptions.mjs';
import { PLANS } from '../../plans.mjs';

const router = Router();

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
    updated_at: row.updated_at,
  };
}

router.get('/', ah(async (req, res) => {
  const rows = await db().query(
    `SELECT * FROM profiles WHERE user_id = $1
     ORDER BY is_default DESC, created_at ASC`,
    [req.user.id],
  );
  const sub = await getEffectiveSubscription(req.user.id);
  const plan = sub && sub.effectiveStatus === 'active' && !sub.expired ? sub.plan : PLANS.free;
  res.json({
    profiles: rows.map(serializeProfile),
    limits: { max_profiles: plan.maxProfiles, max_ram_mb: plan.maxRamMb },
    revision: rows.reduce((max, row) => Math.max(max, new Date(row.updated_at).getTime()), 0),
  });
}));

export default router;
