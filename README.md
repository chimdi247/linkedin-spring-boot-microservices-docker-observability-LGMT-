# LinkedIn system - one-command stack

```bash
cp .env.example .env        # already provided; change secrets for anything but local use
docker compose up -d --build
```
Needs Docker with ~14 GB RAM free (6 JVMs, Elasticsearch, Kafka, MySQL x2, Grafana LGTM stack).

| What | URL | Login |
|---|---|---|
| App (React SPA) | http://localhost:3000 | admin@example.com / password123 |
| API gateway | http://localhost:8080 | |
| Grafana (dashboards, alerts, logs->traces) | http://localhost:3001 | admin / admin |
| Kafka UI | http://localhost:8090 | |
| Redis UI | http://localhost:8091 | |
| MinIO console (S3) | http://localhost:9001 | minioadmin / minioadmin123 |
| Mailpit (alert e-mails) | http://localhost:8025 | |
| ProxySQL (pooled MySQL) | localhost:6033 (admin 6032) | app_rw / app_ro |

## Layout
- `docker-compose.yml`, `.env.example` - everything in one file
- `docker/` - `mysql/init/01-schema.sql` (all tables), `02-seed-admin.sql` (bcrypt-hashed admin), `03-users.sh`, replica bootstrap, ProxySQL config, Kafka (JMX exporter) image, init scripts
- `observability/` - OTel Collector, Mimir/Loki/Tempo (S3 storage on MinIO), Alloy, Grafana (datasources with log->trace links, dashboards, SMTP alerts at 40 % CPU/memory)
- `frontend/` - React + TypeScript + Tailwind SPA (nginx image)
- `tests/` - Cypress, Selenium, integration, smoke, k6 load/stress/spike/soak/breakpoint, JUnit unit tests (see tests/README.md)
- `docs/API-CONTRACT.md` - REST contract + UI test ids

## Production notes
- Point `S3_ENDPOINT`/keys at AWS S3 for Mimir/Loki/Tempo and media; run more replicas with a real ring (memberlist) to scale.
- JVM containers are sized so memory stays near the 40 % alert line; narrow it with `ALERT_CONTAINER_REGEX`.
- Nothing here was executed in the authoring environment (no Docker daemon / package registries) - see the hand-off notes.
