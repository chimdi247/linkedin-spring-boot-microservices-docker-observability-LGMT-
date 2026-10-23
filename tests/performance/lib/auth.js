import http from 'k6/http';
import { check } from 'k6';
import { BASE, JSON_H } from './config.js';

/** Registers `count` users once (setup()) and returns [{token,userId}, ...]. Passwords are throw-away. */
export function registerUsers(count, prefix = 'perf') {
  const users = [];
  const run = Date.now();
  for (let i = 0; i < count; i++) {
    const email = `${prefix}-${run}-${i}@example.com`;
    const r = http.post(`${BASE}/auth/register`, JSON.stringify({
      firstName: 'Perf', lastName: `User${i}`, email, password: 'password123',
      headline: 'Load test user', location: 'Nowhere',
    }), { headers: JSON_H, tags: { name: 'register' } });
    if (check(r, { 'register 201': (x) => x.status === 201 })) {
      users.push({ token: r.json('accessToken'), userId: r.json('userId'), email });
    }
  }
  if (users.length === 0) throw new Error('could not register any perf user - is the stack up?');
  return users;
}

export const authHeaders = (u, extra = {}) => ({ headers: Object.assign({ Authorization: `Bearer ${u.token}` }, extra) });
