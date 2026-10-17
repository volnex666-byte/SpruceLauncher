import { Router } from 'express';
import meRouter from './me.mjs';
import profilesRouter from './profiles.mjs';
import subscriptionRouter from './subscription.mjs';

const router = Router();

router.use('/', meRouter);
router.use('/profiles', profilesRouter);
router.use('/subscription', subscriptionRouter);

export default router;
