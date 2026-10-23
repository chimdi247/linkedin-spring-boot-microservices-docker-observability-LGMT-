// Soak test: moderate constant load for a long time (memory leaks, pool exhaustion, disk growth). env: SOAK_VUS=20 SOAK_DURATION=2h
import { thresholds } from './lib/config.js';
import { registerUsers } from './lib/auth.js';
import { userJourney } from './lib/scenario.js';

export const options = {
  stages: [
    { duration: '2m', target: Number(__ENV.SOAK_VUS || 20) },
    { duration: __ENV.SOAK_DURATION || '2h', target: Number(__ENV.SOAK_VUS || 20) },
    { duration: '1m', target: 0 },
  ],
  thresholds,
};
export function setup() { return registerUsers(Number(__ENV.USERS || 20)); }
export default function (users) { userJourney(users); }
