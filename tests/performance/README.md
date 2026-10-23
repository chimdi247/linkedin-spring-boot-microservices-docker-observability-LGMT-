# Performance tests (k6)

| script | purpose | default |
|---|---|---|
| `load.js` | ramp -> steady -> ramp down | 30 VUs, 5 min hold (`VUS`, `HOLD`) |
| `stress.js` | keep adding load beyond normal | up to 150 VUs (`MAX_VUS`) |
| `spike.js` | sudden 10-20x burst + recovery | 10 -> 200 VUs (`BASE_VUS`, `SPIKE_VUS`) |
| `soak.js` | long constant load | 20 VUs, 2 h (`SOAK_VUS`, `SOAK_DURATION`) |
| `breakpoint.js` | ramping arrival rate until thresholds abort | up to 600 req-iterations/s (`MAX_RATE`, `RAMP`) |

Common: `API_URL` (default `http://localhost:8080`), `USERS` (users registered in `setup()`, default 20).
The traffic mix (60 % browse feed, 20 % search, 15 % create post + like + comment, 5 % profile/notifications) is in `lib/scenario.js`,
shared thresholds (errors < 1 %, p95 < 500 ms ...) in `lib/config.js`.

## Run (no local k6 needed)
```bash
# from tests/performance, with the compose stack up:
docker run --rm -i --network host -v "$PWD":/scripts -w /scripts grafana/k6:0.55.0 run load.js
# inside the compose network instead of the host network:
docker run --rm -i --network linkedin-network -e API_URL=http://api-gateway:8080 -v "$PWD":/scripts -w /scripts grafana/k6:0.55.0 run spike.js
```
With a local k6 binary: `k6 run load.js`.

## Results in Grafana (Mimir)
k6 can remote-write its metrics straight into Mimir (published on host port 9009 only if you add `ports: ["9009:9009"]` to the
`mimir` service; otherwise run k6 on `linkedin-network` and use `http://mimir:9009`):
```bash
docker run --rm -i --network linkedin-network -e API_URL=http://api-gateway:8080 \
  -e K6_PROMETHEUS_RW_SERVER_URL=http://mimir:9009/api/v1/push -e K6_PROMETHEUS_RW_TREND_STATS="p(95),p(99),avg,max" \
  -v "$PWD":/scripts -w /scripts grafana/k6:0.55.0 run -o experimental-prometheus-rw --tag testid=load-$(date +%s) load.js
```
Then query `k6_http_req_duration_p95`, `k6_http_reqs_total`, `k6_vus` in Grafana -> Explore -> Mimir, and watch the service
dashboards / the CPU & memory alerts while the test runs.
