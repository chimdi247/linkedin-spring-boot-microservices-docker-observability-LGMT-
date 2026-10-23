// Spike test: sudden 10x jump, short hold, sudden drop - does the system survive and recover? (env: BASE_VUS, SPIKE_VUS)
import { thresholds } from './lib/config.js';
import { registerUsers } from './lib/auth.js';
import { userJourney } from './lib/scenario.js';

const BASE_VUS = Number(__ENV.BASE_VUS || 10);
const SPIKE = Number(__ENV.SPIKE_VUS || 200);
export const options = {
  stages: [
    { duration: '1m', target: BASE_VUS }, { duration: '10s', target: SPIKE }, { duration: '1m', target: SPIKE },
    { duration: '10s', target: BASE_VUS }, { duration: '3m', target: BASE_VUS }, { duration: '10s', target: 0 },
  ],
  thresholds: { http_req_failed: ['rate<0.05'], http_req_duration: ['p(95)<2500'] },
};
export function setup() { return registerUsers(Number(__ENV.USERS || 20)); }
export default function (users) { userJourney(users); }
