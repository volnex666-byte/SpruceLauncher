import { Router } from 'express';
import { ah } from '../../http.mjs';
import { getEffectiveSubscription, subscriptionSummary } from '../../subscriptions.mjs';

const router = Router();

router.get('/', ah(async (req, res) => {
  const sub = await getEffectiveSubscription(req.user.id);
  res.json({ subscription: subscriptionSummary(sub) });
}));

export default router;
