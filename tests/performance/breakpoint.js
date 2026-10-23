// Breakpoint test: ramp the ARRIVAL RATE until a threshold aborts the run -> the maximum sustainable throughput.
import { thresholds } from './lib/config.js';
import { registerUsers } from './lib/auth.js';
import { userJourney } from './lib/scenario.js';

export const options = {
  scenarios: {
    breakpoint: {
      executor: 'ramping-arrival-rate', startRate: 10, timeUnit: '1s',
      preAllocatedVUs: 200, maxVUs: Number(__ENV.MAX_VUS || 1000),
      stages: [{ duration: __ENV.RAMP || '15m', target: Number(__ENV.MAX_RATE || 600) }],
    },
  },
  thresholds: {
    http_req_failed: [{ threshold: 'rate<0.05', abortOnFail: true, delayAbortEval: '1m' }],
    http_req_duration: [{ threshold: 'p(95)<2000', abortOnFail: true, delayAbortEval: '1m' }],
  },
};
export function setup() { return registerUsers(Number(__ENV.USERS || 20)); }
export default function (users) { userJourney(users); }
