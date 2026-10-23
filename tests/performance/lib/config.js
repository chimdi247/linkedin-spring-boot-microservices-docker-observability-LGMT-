export const API = __ENV.API_URL || 'http://localhost:8080';
export const BASE = `${API}/api/v1`;
export const JSON_H = { 'Content-Type': 'application/json' };

// Shared pass/fail criteria (override per scenario where it makes sense)
export const thresholds = {
  http_req_failed: ['rate<0.01'],                    // < 1 % errors
  http_req_duration: ['p(95)<500', 'p(99)<1500'],    // latency SLO
  'http_req_duration{name:feed}': ['p(95)<400'],
  'http_req_duration{name:create_post}': ['p(95)<800'],
  checks: ['rate>0.99'],
};

export const WORDS = ['java', 'kafka', 'docker', 'react', 'mysql', 'redis', 'grafana', 'observability', 'microservices'];
export const pick = (a) => a[Math.floor(Math.random() * a.length)];
