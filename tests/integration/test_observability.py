"""Observability checks. Mimir / Loki / Tempo are queried THROUGH Grafana's datasource proxy
(they are not published on host ports), so only Grafana (:3001) must be reachable."""
import time
import uuid

import httpx
import pytest

from conftest import GRAFANA_URL, GRAFANA_AUTH, V1, wait_until

pytestmark = [pytest.mark.observability]


@pytest.fixture(scope="module")
def grafana():
    c = httpx.Client(base_url=GRAFANA_URL, auth=GRAFANA_AUTH, timeout=20.0)
    try:
        if c.get("/api/health").status_code != 200:
            pytest.skip("Grafana not healthy")
    except httpx.HTTPError as e:
        pytest.skip(f"Grafana not reachable: {e}")
    yield c
    c.close()


def _ds(g, uid):
    return f"/api/datasources/proxy/uid/{uid}"


@pytest.mark.parametrize("uid", ["mimir", "loki", "tempo"])
def test_datasource_provisioned_and_healthy(grafana, uid):
    assert grafana.get(f"/api/datasources/uid/{uid}").status_code == 200
    h = grafana.get(f"/api/datasources/uid/{uid}/health")
    assert h.status_code == 200, h.text


def test_loki_datasource_has_trace_derived_field(grafana):
    ds = grafana.get("/api/datasources/uid/loki").json()
    fields = ds["jsonData"]["derivedFields"]
    assert fields and fields[0]["datasourceUid"] == "tempo" and fields[0]["matcherRegex"] == "trace_id"


def test_tempo_datasource_links_to_logs_and_metrics(grafana):
    j = grafana.get("/api/datasources/uid/tempo").json()["jsonData"]
    assert j["tracesToLogsV2"]["datasourceUid"] == "loki"
    assert j["serviceMap"]["datasourceUid"] == "mimir"


def test_dashboards_are_provisioned(grafana):
    titles = " | ".join(d["title"] for d in grafana.get("/api/search", params={"type": "dash-db"}).json()).lower()
    for word in ("kafka", "mysql", "proxysql", "redis"):
        assert word in titles, f"no {word} dashboard in: {titles}"


def test_cpu_and_memory_40_percent_alert_rules_provisioned(grafana):
    rules = grafana.get("/api/v1/provisioning/alert-rules").json()
    titles = {r["title"] for r in rules}
    assert {"Container CPU above 40%", "Container memory above 40%", "Node CPU above 40%", "Node memory above 40%"} <= titles
    for r in rules:
        thr = [d for d in r["data"] if d["model"].get("type") == "threshold"]
        assert thr and thr[0]["model"]["conditions"][0]["evaluator"]["params"] == [40]


def test_email_contact_point_provisioned(grafana):
    cps = grafana.get("/api/v1/provisioning/contact-points").json()
    assert any(c["type"] == "email" for c in cps)


def test_mimir_has_service_metrics(grafana):
    def has_series():
        r = grafana.get(_ds(grafana, "mimir") + "/api/v1/query", params={"query": "count(http_server_request_duration_seconds_count)"})
        return r.status_code == 200 and r.json()["data"]["result"]
    wait_until(has_series, 90, 5, "no HTTP server metrics from the services in Mimir")


def test_mimir_scrapes_infra_exporters(grafana):
    for job in ("cadvisor", "node-exporter", "kafka-jmx", "kafka-exporter", "redis", "mysql-primary"):
        r = grafana.get(_ds(grafana, "mimir") + "/api/v1/query", params={"query": f'up{{job="{job}"}}'})
        res = r.json()["data"]["result"]
        assert res and res[0]["value"][1] == "1", f"{job} is not up"


def test_request_trace_is_findable_in_tempo_and_logs_carry_trace_id(http, stack_up, grafana):
    # generate traffic that fans out gateway -> user-service -> MySQL, tagged by a unique e-mail
    email = f"trace-{uuid.uuid4().hex[:10]}@example.com"
    start = int(time.time()) - 5
    r = http.post(f"{V1}/auth/register", json={"firstName": "Trace", "lastName": "Me", "email": email, "password": "secret1"})
    assert r.status_code == 201

    def trace_found():
        q = '{ resource.service.name = "user-service" && span.http.route =~ ".*auth/register.*" }'
        res = grafana.get(_ds(grafana, "tempo") + "/api/search", params={"q": q, "start": start, "end": int(time.time()) + 5, "limit": 5})
        return res.status_code == 200 and res.json().get("traces")
    traces = wait_until(trace_found, 90, 3, "register trace not found in Tempo")
    tid = traces[0]["traceID"]

    full = grafana.get(_ds(grafana, "tempo") + f"/api/traces/{tid}")
    assert full.status_code == 200
    services = {a["value"]["stringValue"]
                for b in full.json().get("batches", []) for a in b["resource"]["attributes"] if a["key"] == "service.name"}
    assert {"api-gateway", "user-service"} <= services, f"context propagation broke, services in trace: {services}"

    def log_with_trace():
        res = grafana.get(_ds(grafana, "loki") + "/loki/api/v1/query_range", params={
            "query": '{service_name="user-service"} | trace_id != ""', "start": (start - 60) * 10**9,
            "end": (int(time.time()) + 60) * 10**9, "limit": 20})
        return res.status_code == 200 and res.json()["data"]["result"]
    wait_until(log_with_trace, 90, 3, "no user-service logs with trace_id structured metadata in Loki")


def test_infra_container_logs_reach_loki(grafana):
    def any_logs():
        res = grafana.get(_ds(grafana, "loki") + "/loki/api/v1/query_range", params={
            "query": '{service_name=~"mysql-primary|kafka|redis"}', "limit": 5,
            "start": (int(time.time()) - 3600) * 10**9, "end": (int(time.time()) + 60) * 10**9})
        return res.status_code == 200 and res.json()["data"]["result"]
    wait_until(any_logs, 60, 3, "Alloy is not shipping infrastructure logs")
