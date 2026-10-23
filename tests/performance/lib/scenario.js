import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { BASE, WORDS, pick } from './config.js';
import { authHeaders } from './auth.js';

/** One virtual-user iteration mixing the real traffic profile: 60 % browse, 20 % search, 15 % write, 5 % social. */
export function userJourney(users) {
  const me = users[(__VU - 1) % users.length];
  const other = users[Math.floor(Math.random() * users.length)];
  const h = authHeaders(me);
  const dice = Math.random();

  if (dice < 0.60) {
    group('browse feed', () => {
      const f = http.get(`${BASE}/feed/${me.userId}?page=0&size=10`, Object.assign({ tags: { name: 'feed' } }, h));
      check(f, { 'feed 200': (r) => r.status === 200 });
      const ids = f.status === 200 ? f.json() : [];
      if (ids.length) {
        const p = http.get(`${BASE}/posts?ids=${ids.join(',')}`, Object.assign({ tags: { name: 'posts_batch' } }, h));
        check(p, { 'posts batch 200': (r) => r.status === 200 });
      }
      check(http.get(`${BASE}/users/suggestions?limit=6`, Object.assign({ tags: { name: 'suggestions' } }, h)),
        { 'suggestions 200': (r) => r.status === 200 });
    });
  } else if (dice < 0.80) {
    group('search', () => {
      const t = pick(['people', 'posts', 'skills']);
      const q = t === 'skills' ? `skill=${pick(WORDS)}` : `q=${pick(WORDS)}`;
      check(http.get(`${BASE}/search/${t}?${q}`, Object.assign({ tags: { name: 'search' } }, h)),
        { 'search 200': (r) => r.status === 200 });
    });
  } else if (dice < 0.95) {
    group('write', () => {
      const r = http.post(`${BASE}/posts`, { content: `load test ${pick(WORDS)} ${Date.now()}` },
        Object.assign({ tags: { name: 'create_post' } }, h));
      if (check(r, { 'post 201': (x) => x.status === 201 })) {
        const id = r.json('id');
        check(http.post(`${BASE}/posts/${id}/like`, null, Object.assign({ tags: { name: 'like' } }, h)),
          { 'like 200': (x) => x.status === 200 });
        check(http.post(`${BASE}/posts/${id}/comments`, { content: 'nice' }, Object.assign({ tags: { name: 'comment' } }, h)),
          { 'comment 201': (x) => x.status === 201 });
      }
    });
  } else {
    group('social', () => {
      http.get(`${BASE}/users/${other.userId}`, Object.assign({ tags: { name: 'profile' } }, h));
      http.get(`${BASE}/notifications/unread-count`, Object.assign({ tags: { name: 'notifications' } }, h));
    });
  }
  sleep(Math.random() * 1.5 + 0.5);
}
