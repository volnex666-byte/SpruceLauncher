export const PLANS = {
  free: { title: 'Обычный', maxProfiles: 1, maxRamMb: 2048, pricePerMonthCents: 5000 },
  premium: { title: 'Креатор', maxProfiles: 10, maxRamMb: 8192, pricePerMonthCents: 32000 },
  legendary: { title: 'Legendary', maxProfiles: 999, maxRamMb: 32768, pricePerMonthCents: 99900 },
};

export const PLAN_ORDER = ['free', 'premium', 'legendary'];
