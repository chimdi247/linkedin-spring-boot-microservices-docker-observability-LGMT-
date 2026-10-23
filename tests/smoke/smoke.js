// k6 smoke test: 1 VU, a handful of iterations, strict thresholds.
//   k6 run smoke.js                 (env: API_URL, BASE_URL)
import http from 'k6/http';
import { check, group } from 'k6';

const API = __ENV.API_URL || 'http://localhost:8080';
const BASE = __ENV.BASE_URL || 'http://localhost:3000';
const JSON_H = { headers: { 'Content-Type': 'application/json' } };

export const options = {
  vus: 1,
  iterations: 3,
  thresholds: { checks: ['rate==1'], http_req_failed: ['rate<0.01'], http_req_duration: ['p(95)<1500'] },
};

export default function () {
  group('frontend', () => {
    check(http.get(`${BASE}/`), { 'index 200': (r) => r.status === 200 });
    check(http.get(`${BASE}/healthz`), { 'healthz 200': (r) => r.status === 200 });
  });
  const login = http.post(`${API}/api/v1/auth/login`,
    JSON.stringify({ email: 'admin@example.com', password: 'password123' }), JSON_H);
  check(login, { 'login 200': (r) => r.status === 200, 'has token': (r) => !!r.json('accessToken') });
  const token = login.json('accessToken');
  const uid = login.json('userId');
  const h = { headers: { Authorization: `Bearer ${token}` } };
  group('authenticated reads', () => {
    check(http.get(`${API}/api/v1/users/${uid}`, h), { 'profile 200': (r) => r.status === 200 });
    check(http.get(`${API}/api/v1/feed/${uid}`, h), { 'feed 200': (r) => r.status === 200 });
    check(http.get(`${API}/api/v1/search/people?q=admin`, h), { 'search 200': (r) => r.status === 200 });
    check(http.get(`${API}/api/v1/notifications`, h), { 'notifications 200': (r) => r.status === 200 });
  });
  check(http.get(`${API}/api/v1/users/suggestions`), { 'no token -> 401': (r) => r.status === 401 });
}
