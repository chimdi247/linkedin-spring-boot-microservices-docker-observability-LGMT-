# Frontend

React 18 + TypeScript + Tailwind 4 + Vite SPA for the LinkedIn microservices.

- `npm install && npm run dev` → http://localhost:5173 (proxies `/api`, `/otlp`, `/media` like nginx does)
- `npm test` → vitest unit tests, `npm run build` → type-check + production build
- In Docker the image is built by the root `docker-compose.yml` (nginx on :3000)
- API contract and `data-testid` list: `../docs/API-CONTRACT.md` (used by the Cypress / Selenium tests – keep stable)
- Browser tracing (`src/telemetry.ts`): service `frontend`, OTLP to `/otlp/v1/traces`, W3C `traceparent` on every `/api` call so browser spans and backend spans share one trace in Grafana/Tempo. Failures to reach the collector never affect the app.
