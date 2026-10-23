# Tests

Everything except `unit` runs against the **live compose stack** (`docker compose up -d --build` at the repo root).
Seeded login for UI tests: `admin@example.com` / `password123`. All suites are configurable through env vars
(`API_URL` gateway, default `http://localhost:8080` · `BASE_URL` SPA, default `http://localhost:3000` · `GRAFANA_URL` default `http://localhost:3001`).

| suite | folder | run (from `tests/`) | what it covers |
|---|---|---|---|
| Smoke | `smoke/` | `make smoke` (bash+curl, <30 s) · `k6 run smoke/smoke.js` | health, admin login, profile/feed/search/notifications, SPA served, Grafana + datasources |
| Unit (backend) | `unit/backend/<service>/` | `make unit` or `cd ../user-service && mvn test` | JUnit 5 + Mockito: auth, connections, posts, feed fan-out, search, notifications, JWT filter |
| Unit (frontend) | `../frontend/` | `make unit-frontend` | vitest, owned by the frontend project |
| Integration | `integration/` | `make venv-integration && make integration` | pytest+httpx through the gateway: auth, JWT enforcement, users, posts, feed (Kafka fan-out), search, notifications, Kafka topics, ProxySQL/MySQL |
| Observability | `integration/` | `make observability` | Grafana datasources, derived log->trace link, 40 % alert rules, metrics in Mimir, a request trace spanning gateway+user-service in Tempo, `trace_id` on Loki logs |
| E2E (Cypress 13) | `e2e/cypress/` | `make e2e` (or `cd e2e/cypress && npm i && npx cypress open`) | register/login/logout/401 redirect, posts/likes/comments, network, profile, search (polls for async indexing), notifications |
| Selenium | `selenium/` | `make venv-selenium && make selenium` (`HEADLESS=0` to watch) | page-object UI tests, headless Chrome via Selenium Manager (Chrome/Chromium must be installed) |
| Performance (k6) | `performance/` | `make load` · `stress` · `spike` · `soak` · `breakpoint` | see `performance/README.md` (runs in the `grafana/k6` image, can remote-write into Mimir) |

`./run-all.sh` runs smoke + integration + observability + e2e + selenium and prints a summary (`--skip-e2e`, `--skip-ui`).

## Notes
* **Eventual consistency**: feed, search and notifications are filled from Kafka events. Tests poll (`wait_until` / `cy.pollApi`) instead of sleeping.
* Tests create unique users each run (`it-<uuid>@example.com`), so they can be re-run without cleanup.
* The `data-testid` values used by Cypress/Selenium are the ones listed in `docs/API-CONTRACT.md`.
* Mimir/Loki/Tempo are not published on host ports: observability tests go through Grafana's datasource proxy (`:3001`, admin/admin).
  `test_infra.py` needs host access to Kafka (`9092`), MySQL primary (`3307`) and ProxySQL (`6033`); each test skips itself when its port or driver is missing.
* Unit tests are compiled by Maven through `<testSourceDirectory>../tests/unit/backend/<service></testSourceDirectory>` in each service pom.
  The Dockerfiles build with `-Dmaven.test.skip=true`, which skips test compilation and test resources entirely, so the image build does not depend on this folder
  (and Maven ignores a missing source directory anyway). Note the docker build context is the service folder only, so `../tests` is not even visible inside it.
* Not covered on purpose: destructive chaos tests (killing containers). `performance/breakpoint.js` intentionally pushes the stack until it breaks.
