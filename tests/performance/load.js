// Load test: ramp to a realistic steady load, hold, ramp down. k6 run load.js (env: VUS, HOLD)
import { thresholds } from './lib/config.js';
import { registerUsers } from './lib/auth.js';
import { userJourney } from './lib/scenario.js';

const VUS = Number(__ENV.VUS || 30);
const HOLD = __ENV.HOLD || '5m';
export const options = {
  stages: [{ duration: '1m', target: VUS }, { duration: HOLD, target: VUS }, { duration: '30s', target: 0 }],
  thresholds,
};
export function setup() { return registerUsers(Number(__ENV.USERS || 20)); }
export default function (users) { userJourney(users); }
