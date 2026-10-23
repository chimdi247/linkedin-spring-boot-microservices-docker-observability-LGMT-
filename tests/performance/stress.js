// Stress test: keep adding load beyond normal to find where latency/errors start degrading.
import { thresholds } from './lib/config.js';
import { registerUsers } from './lib/auth.js';
import { userJourney } from './lib/scenario.js';

const MAX = Number(__ENV.MAX_VUS || 150);
export const options = {
  stages: [
    { duration: '2m', target: Math.round(MAX * 0.25) }, { duration: '3m', target: Math.round(MAX * 0.5) },
    { duration: '3m', target: Math.round(MAX * 0.75) }, { duration: '3m', target: MAX }, { duration: '2m', target: 0 },
  ],
  // looser: the point is to observe the degradation, but keep a hard error ceiling
  thresholds: { http_req_failed: ['rate<0.05'], http_req_duration: ['p(95)<2000'] },
};
export function setup() { return registerUsers(Number(__ENV.USERS || 20)); }
export default function (users) { userJourney(users); }
