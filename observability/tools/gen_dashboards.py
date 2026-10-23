#!/usr/bin/env python3
"""Generates the Grafana dashboards in ../grafana/dashboards (stdlib only).  Run:  python3 gen_dashboards.py
Colour discipline: one hue family per meaning - green=good/success, red=bad/failed, amber=warning,
blue/purple/teal for neutral categorical series; thresholds always shown as lines/colour steps."""
import json, os

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "grafana", "dashboards")
M = {"type": "prometheus", "uid": "mimir"}
L = {"type": "loki", "uid": "loki"}
T = {"type": "tempo", "uid": "tempo"}
GREEN, RED, AMBER, BLUE = "#2ca02c", "#d62728", "#e0a000", "#3274d9"
SERVICES = "api-gateway|user-service|post-service|feed-service|search-service|notification-service"


class Dash:
    def __init__(self, uid, title, desc, tags, variables=None):
        self.uid, self.title, self.desc, self.tags = uid, title, desc, tags
        self.panels, self.vars, self._id, self.x, self.y, self.rowh = [], variables or [], 0, 0, 0, 0

    def _pos(self, w, h):
        if self.x + w > 24:
            self.x, self.y, self.rowh = 0, self.y + self.rowh, 0
        pos = {"x": self.x, "y": self.y, "w": w, "h": h}
        self.x += w
        self.rowh = max(self.rowh, h)
        return pos

    def _add(self, p, w, h):
        self._id += 1
        p["id"] = self._id
        p["gridPos"] = self._pos(w, h)
        self.panels.append(p)

    def row(self, title):
        self.x, self.y, self.rowh = 0, self.y + self.rowh, 0
        self._id += 1
        self.panels.append({"id": self._id, "type": "row", "title": title, "collapsed": False,
                            "gridPos": {"x": 0, "y": self.y, "w": 24, "h": 1}, "panels": []})
        self.y += 1
        self.rowh = 0

    def text(self, title, md, w=24, h=5):
        self._add({"type": "text", "title": title, "options": {"mode": "markdown", "content": md}}, w, h)

    @staticmethod
    def targets(qs, ds=M):
        out = []
        for i, q in enumerate(qs):
            if isinstance(q, str):
                q = (q, "")
            expr, leg = q[0], q[1]
            t = {"refId": chr(65 + i), "datasource": ds, "expr": expr, "legendFormat": leg}
            if ds is M:
                t["range"] = True
                t["editorMode"] = "code"
            out.append(t)
        return out

    def ts(self, title, qs, unit="short", desc="", w=8, h=8, stack=False, thresh=None, mn=None, mx=None, bars=False):
        custom = {"drawStyle": "bars" if bars else "line", "lineWidth": 1, "fillOpacity": 25 if stack else 8,
                  "showPoints": "never", "spanNulls": False,
                  "stacking": {"mode": "normal" if stack else "none", "group": "A"}}
        if thresh is not None:
            custom["thresholdsStyle"] = {"mode": "line"}
        d = {"unit": unit, "custom": custom,
             "thresholds": {"mode": "absolute", "steps": [{"color": GREEN, "value": None}] +
                            ([{"color": RED, "value": thresh}] if thresh is not None else [])}}
        if mn is not None: d["min"] = mn
        if mx is not None: d["max"] = mx
        self._add({"type": "timeseries", "title": title, "description": desc, "datasource": M,
                   "targets": self.targets(qs), "fieldConfig": {"defaults": d, "overrides": []},
                   "options": {"legend": {"displayMode": "table", "placement": "bottom", "calcs": ["lastNotNull", "max"]},
                               "tooltip": {"mode": "multi", "sort": "desc"}}}, w, h)

    def stat(self, title, expr, unit="short", steps=None, desc="", w=4, h=4, mappings=None, graph=True, decimals=None):
        steps = steps or [{"color": BLUE, "value": None}]
        d = {"unit": unit, "thresholds": {"mode": "absolute", "steps": steps}, "mappings": mappings or []}
        if decimals is not None: d["decimals"] = decimals
        self._add({"type": "stat", "title": title, "description": desc, "datasource": M,
                   "targets": self.targets([expr]), "fieldConfig": {"defaults": d, "overrides": []},
                   "options": {"reduceOptions": {"calcs": ["lastNotNull"], "fields": "", "values": False},
                               "colorMode": "background" if len(steps) > 1 else "value",
                               "graphMode": "area" if graph else "none", "textMode": "auto"}}, w, h)

    def gauge(self, title, expr, unit="percent", steps=None, desc="", w=4, h=5, mx=100):
        self._add({"type": "gauge", "title": title, "description": desc, "datasource": M,
                   "targets": self.targets([expr]),
                   "fieldConfig": {"defaults": {"unit": unit, "min": 0, "max": mx,
                                                "thresholds": {"mode": "absolute", "steps": steps or [{"color": GREEN, "value": None}]}},
                                   "overrides": []},
                   "options": {"reduceOptions": {"calcs": ["lastNotNull"]}, "showThresholdMarkers": True}}, w, h)

    def bars(self, title, qs, unit="short", desc="", w=8, h=8):
        self._add({"type": "bargauge", "title": title, "description": desc, "datasource": M,
                   "targets": [dict(t, instant=True, range=False) for t in self.targets(qs)],
                   "fieldConfig": {"defaults": {"unit": unit, "thresholds": {"mode": "absolute", "steps": [{"color": BLUE, "value": None}]}}, "overrides": []},
                   "options": {"orientation": "horizontal", "displayMode": "gradient", "reduceOptions": {"calcs": ["lastNotNull"]}}}, w, h)

    def table(self, title, qs, desc="", w=12, h=8, unit="short"):
        self._add({"type": "table", "title": title, "description": desc, "datasource": M,
                   "targets": [dict(t, instant=True, range=False, format="table") for t in self.targets(qs)],
                   "fieldConfig": {"defaults": {"unit": unit}, "overrides": []},
                   "transformations": [{"id": "merge", "options": {}}],
                   "options": {"showHeader": True}}, w, h)

    def logs(self, title, expr, w=24, h=10):
        self._add({"type": "logs", "title": title, "datasource": L,
                   "description": "Click a line, then the 'View trace in Tempo' link (derived field on the trace_id structured metadata) to open the trace and its spans.",
                   "targets": [{"refId": "A", "datasource": L, "expr": expr, "queryType": "range"}],
                   "options": {"showTime": True, "wrapLogMessage": True, "enableLogDetails": True, "sortOrder": "Descending",
                               "dedupStrategy": "none", "prettifyLogMessage": False}}, w, h)

    def traces_table(self, title, w=12, h=9):
        self._add({"type": "table", "title": title, "datasource": T,
                   "description": "TraceQL search; click a Trace ID to open the waterfall of spans (and jump to the logs of a span).",
                   "targets": [{"refId": "A", "datasource": T, "queryType": "traceql", "limit": 20, "tableType": "traces",
                                "query": '{ resource.service.namespace = "linkedin" }'}],
                   "fieldConfig": {"defaults": {}, "overrides": []}, "options": {"showHeader": True}}, w, h)

    def nodegraph(self, title, w=12, h=9):
        self._add({"type": "nodeGraph", "title": title, "datasource": T,
                   "description": "Service map generated by Tempo's metrics-generator (service_graphs) from span relationships.",
                   "targets": [{"refId": "A", "datasource": T, "queryType": "serviceMap"}], "options": {}}, w, h)

    def build(self, links_to):
        links = [{"type": "dashboards", "title": "LinkedIn dashboards", "tags": ["linkedin"], "asDropdown": False,
                  "includeVars": False, "keepTime": True, "icon": "external link"}]
        return {"uid": self.uid, "title": self.title, "description": self.desc, "tags": ["linkedin"] + self.tags,
                "schemaVersion": 39, "version": 1, "editable": True, "graphTooltip": 1, "refresh": "30s",
                "time": {"from": "now-1h", "to": "now"}, "timezone": "browser", "links": links,
                "templating": {"list": self.vars}, "annotations": {"list": []}, "panels": self.panels}


def qvar(name, query, label=None, multi=True, default_all=True, ds=M, regex=""):
    v = {"name": name, "label": label or name, "type": "query", "datasource": ds, "query": {"query": query, "refId": name},
         "definition": query, "refresh": 2, "sort": 1, "multi": multi, "includeAll": multi, "regex": regex,
         "current": {}}
    if multi and default_all:
        v["current"] = {"text": "All", "value": "$__all"}
    if multi:
        v["allValue"] = ".*"
    return v


def save(d):
    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(OUT, d.uid + ".json"), "w") as f:
        json.dump(d.build(None), f, indent=2)
    return d


# ----------------------------------------------------------------------------- services
def services():
    S = 'service_name=~"$service"'
    rq = f'http_server_request_duration_seconds_count{{{S}}}'
    d = Dash("linkedin-services", "LinkedIn - Services, SLO & Business KPIs",
             "Uptime, latency, errors, SLI/SLO, business KPIs, JVM, containers and nodes for every service.", ["services", "slo", "home"],
             [qvar("service", f'label_values(http_server_request_duration_seconds_count, service_name)', "Service"),
              qvar("container", 'label_values(container_memory_working_set_bytes{name!=""}, name)', "Container"),
              {"name": "level", "label": "Log level", "type": "custom", "multi": True, "includeAll": True, "allValue": ".*",
               "query": "ERROR,WARN,INFO,DEBUG", "current": {"text": "All", "value": "$__all"},
               "options": []}])
    d.text("About this dashboard",
           "**Service SLOs** (30-day style targets evaluated over the selected time range):\n\n"
           "* **Availability SLI** = requests that did *not* return 5xx / all requests. **SLO 99.5 %** -> error budget 0.5 %.\n"
           "* **Latency SLI** = share of requests answered in < 500 ms. **SLO 95 %**.\n"
           "* **Burn rate** = (observed error ratio) / (allowed error ratio). 1 = budget consumed exactly at the SLO pace; >1 burns faster (page at 14.4 over 1h).\n\n"
           "Request metrics come from the OpenTelemetry Java agent (`http_server_request_duration_seconds_*`), business KPIs from the manual `Telemetry` classes, "
           "uptime from the collector's `httpcheck` probes. Related: [Kafka](/d/linkedin-kafka) · [MySQL](/d/linkedin-mysql) · [ProxySQL](/d/linkedin-proxysql) · [Redis](/d/linkedin-redis).", h=4)

    d.row("Uptime & availability")
    d.stat("Services up", 'count(max by (http_url) (httpcheck_status{http_status_class="2xx"}) == 1)', desc="Number of health endpoints answering 2xx (7 expected incl. frontend).",
           steps=[{"color": RED, "value": None}, {"color": AMBER, "value": 6}, {"color": GREEN, "value": 7}], w=4)
    d.stat("Overall availability SLI", f'100 * (1 - (sum(rate(http_server_request_duration_seconds_count{{{S},http_response_status_code=~"5.."}}[$__range])) or vector(0)) / sum(rate({rq}[$__range])))',
           unit="percent", decimals=3, desc="Share of non-5xx responses over the selected range.",
           steps=[{"color": RED, "value": None}, {"color": AMBER, "value": 99}, {"color": GREEN, "value": 99.5}], w=4)
    d.stat("Error budget remaining", f'100 * (1 - ((sum(rate(http_server_request_duration_seconds_count{{{S},http_response_status_code=~"5.."}}[$__range])) or vector(0)) / sum(rate({rq}[$__range]))) / 0.005)',
           unit="percent", decimals=1, desc="100 % = no 5xx; 0 % = the 0.5 % budget of the 99.5 % SLO is used up; negative = SLO breached.",
           steps=[{"color": RED, "value": None}, {"color": AMBER, "value": 25}, {"color": GREEN, "value": 50}], w=4)
    d.stat("Latency SLI (< 500 ms)", f'100 * sum(rate(http_server_request_duration_seconds_bucket{{{S},le="0.5"}}[$__range])) / sum(rate({rq}[$__range]))',
           unit="percent", decimals=2, desc="SLO: 95 % of requests under 500 ms.",
           steps=[{"color": RED, "value": None}, {"color": AMBER, "value": 90}, {"color": GREEN, "value": 95}], w=4)
    d.stat("Burn rate (5m)", f'((sum(rate(http_server_request_duration_seconds_count{{{S},http_response_status_code=~"5.."}}[5m])) or vector(0)) / sum(rate({rq}[5m]))) / 0.005',
           decimals=2, desc="Error-budget burn rate over the last 5 minutes (1 = on budget).",
           steps=[{"color": GREEN, "value": None}, {"color": AMBER, "value": 1}, {"color": RED, "value": 6}], w=4)
    d.stat("Gateway auth rejected / min", 'sum(rate(gateway_auth_rejected_total[5m])) * 60', desc="401s issued by the API gateway.", steps=[{"color": GREEN, "value": None}, {"color": AMBER, "value": 30}], w=4)
    d.ts("Endpoint health (httpcheck, 1 = 2xx)", [('max by (http_url) (httpcheck_status{http_status_class="2xx"})', "{{http_url}}")], desc="Probes every 15 s from the collector.", w=12, mn=0, mx=1.1)
    d.ts("Probe latency", [('avg by (http_url) (httpcheck_duration)/1000', "{{http_url}}")], unit="s", desc="httpcheck_duration is reported in ms.", w=12)
    d.ts("Process uptime", [('max by (service_name) (app_uptime_seconds)', "{{service_name}}")], unit="s", desc="Seconds since the JVM started; drops reveal restarts.", w=12)
    d.ts("Scrape targets up", [('up', "{{job}}")], desc="Prometheus-style `up` for every scraped exporter/infra target.", w=12, mn=0, mx=1.1)

    d.row("Traffic, latency & errors")
    d.ts("Request rate by service", [(f'sum by (service_name) (rate({rq}[$__rate_interval]))', "{{service_name}}")], unit="reqps", w=8, stack=True)
    for q, n in ((0.5, "p50"), (0.95, "p95"), (0.99, "p99")):
        pass
    d.ts("Latency p50 / p95 / p99 (selected services)", [
        (f'histogram_quantile(0.50, sum by (le) (rate(http_server_request_duration_seconds_bucket{{{S}}}[$__rate_interval])))', "p50"),
        (f'histogram_quantile(0.95, sum by (le) (rate(http_server_request_duration_seconds_bucket{{{S}}}[$__rate_interval])))', "p95"),
        (f'histogram_quantile(0.99, sum by (le) (rate(http_server_request_duration_seconds_bucket{{{S}}}[$__rate_interval])))', "p99")],
        unit="s", w=8, thresh=0.5, desc="Red line = 500 ms latency SLO threshold. Exemplars link to traces.")
    d.ts("p95 latency by service", [(f'histogram_quantile(0.95, sum by (service_name, le) (rate(http_server_request_duration_seconds_bucket{{{S}}}[$__rate_interval])))', "{{service_name}}")], unit="s", w=8)
    d.ts("5xx error rate by service", [(f'sum by (service_name) (rate(http_server_request_duration_seconds_count{{{S},http_response_status_code=~"5.."}}[$__rate_interval]))', "{{service_name}}")], unit="reqps", w=8)
    d.ts("Error ratio (5xx / all)", [(f'100 * sum by (service_name) (rate(http_server_request_duration_seconds_count{{{S},http_response_status_code=~"5.."}}[$__rate_interval])) / sum by (service_name) (rate({rq}[$__rate_interval]))', "{{service_name}}")], unit="percent", w=8, thresh=0.5, desc="Red line = 0.5 % budget.")
    d.ts("Responses by status class", [
        (f'sum by (http_response_status_code) (rate({rq}[$__rate_interval]))', "{{http_response_status_code}}")], unit="reqps", w=8, stack=True)
    d.stat("Successful requests (2xx/3xx)", f'sum(increase(http_server_request_duration_seconds_count{{{S},http_response_status_code=~"[23].."}}[$__range]))', steps=[{"color": GREEN, "value": None}], w=6, desc="Total over the selected time range.")
    d.stat("Failed requests (4xx/5xx)", f'sum(increase(http_server_request_duration_seconds_count{{{S},http_response_status_code=~"[45].."}}[$__range])) or vector(0)', steps=[{"color": RED, "value": None}], w=6, desc="Total over the selected time range.")
    d.ts("Successful vs failed requests / min", [
        (f'sum(rate(http_server_request_duration_seconds_count{{{S},http_response_status_code=~"[23].."}}[$__rate_interval])) * 60', "successful (2xx/3xx)"),
        (f'sum(rate(http_server_request_duration_seconds_count{{{S},http_response_status_code=~"[45].."}}[$__rate_interval])) * 60 or vector(0)', "failed (4xx/5xx)")],
        w=12, stack=True)
    d.table("Slowest routes (p95, last 15m)", [(f'topk(10, histogram_quantile(0.95, sum by (service_name, http_route, le) (rate(http_server_request_duration_seconds_bucket{{{S}}}[15m]))))', "")], unit="s", w=12, h=7)
    d.table("Top routes by 5xx (last 15m)", [(f'topk(10, sum by (service_name, http_route, http_response_status_code) (increase(http_server_request_duration_seconds_count{{{S},http_response_status_code=~"5.."}}[15m])))', "")], w=12, h=7)

    d.row("Business KPIs")
    d.stat("Users registered", 'sum(increase(users_registered_total[$__range]))', w=4, steps=[{"color": BLUE, "value": None}])
    d.stat("Logins ok", 'sum(increase(auth_logins_total{outcome="success"}[$__range]))', w=4, steps=[{"color": GREEN, "value": None}])
    d.stat("Logins failed", 'sum(increase(auth_logins_total{outcome="failure"}[$__range])) or vector(0)', w=4, steps=[{"color": AMBER, "value": None}])
    d.stat("Posts created", 'sum(increase(posts_created_total[$__range]))', w=4, steps=[{"color": BLUE, "value": None}])
    d.stat("Connections accepted", 'sum(increase(connections_accepted_total[$__range]))', w=4, steps=[{"color": BLUE, "value": None}])
    d.stat("Comments added", 'sum(increase(comments_added_total[$__range]))', w=4, steps=[{"color": BLUE, "value": None}])
    d.ts("Sign-ups, logins & failures / min", [('sum(rate(users_registered_total[$__rate_interval])) * 60', "registrations"),
        ('sum by (outcome) (rate(auth_logins_total[$__rate_interval])) * 60', "logins {{outcome}}")], w=8)
    d.ts("Connections requested vs accepted / min", [('sum(rate(connections_requested_total[$__rate_interval])) * 60', "requested"),
        ('sum(rate(connections_accepted_total[$__rate_interval])) * 60', "accepted"), ('sum(rate(profile_updates_total[$__rate_interval])) * 60', "profile updates")], w=8)
    d.ts("Content activity / min", [('sum by (has_image) (rate(posts_created_total[$__rate_interval])) * 60', "posts has_image={{has_image}}"),
        ('sum(rate(posts_deleted_total[$__rate_interval])) * 60', "posts deleted"), ('sum by (action) (rate(post_likes_total[$__rate_interval])) * 60', "likes {{action}}"),
        ('sum(rate(comments_added_total[$__rate_interval])) * 60', "comments")], w=8)
    d.ts("Feed fan-out recipients (avg / p95)", [('sum(rate(feed_fanout_recipients_sum[$__rate_interval])) / sum(rate(feed_fanout_recipients_count[$__rate_interval]))', "avg recipients per post"),
        ('histogram_quantile(0.95, sum by (le) (rate(feed_fanout_recipients_bucket[$__rate_interval])))', "p95")], w=8)
    d.ts("Feed events & reads", [('sum by (outcome) (rate(feed_events_total[$__rate_interval])) * 60', "events {{outcome}}"),
        ('sum by (result) (rate(feed_reads_total[$__rate_interval])) * 60', "reads {{result}}")], w=8)
    d.ts("Search requests & indexing / min", [('sum by (type) (rate(search_requests_total[$__rate_interval])) * 60', "search {{type}}"),
        ('sum by (index, outcome) (rate(search_index_operations_total[$__rate_interval])) * 60', "index {{index}} {{outcome}}")], w=8)
    d.ts("Notifications created / min", [('sum by (type, outcome) (rate(notifications_created_total[$__rate_interval])) * 60', "{{type}} {{outcome}}")], w=8, stack=True)
    d.ts("Gateway auth accepted vs rejected / min", [('sum(rate(gateway_auth_accepted_total[$__rate_interval])) * 60', "accepted"),
        ('sum by (reason) (rate(gateway_auth_rejected_total[$__rate_interval])) * 60', "rejected {{reason}}")], w=8)

    d.row("JVM")
    d.ts("Heap used", [('sum by (service_name) (jvm_memory_used_bytes{jvm_memory_type="heap"})', "{{service_name}}")], unit="bytes", w=8)
    d.ts("JVM CPU utilisation", [('jvm_cpu_recent_utilization_ratio', "{{service_name}}")], unit="percentunit", w=8, desc="Gauge with unit 1 -> Prometheus name carries the _ratio suffix.")
    d.ts("Threads & loaded classes", [('jvm_thread_count', "threads {{service_name}}")], w=8)
    d.ts("GC time (share of wall clock)", [('sum by (service_name) (rate(jvm_gc_duration_seconds_sum[$__rate_interval]))', "{{service_name}}")], unit="percentunit", w=12)
    d.ts("GC pauses / s", [('sum by (service_name) (rate(jvm_gc_duration_seconds_count[$__rate_interval]))', "{{service_name}}")], w=12)

    cpu = 'sum by (name) (rate(container_cpu_usage_seconds_total{name=~"$container"}[$__rate_interval]))'
    d.row("Containers (alert threshold 40 % of the limit)")
    d.ts("Container CPU % of limit", [(f'100 * {cpu} / (sum by (name) (container_spec_cpu_quota{{name=~"$container"}} > 0) / sum by (name) (container_spec_cpu_period{{name=~"$container"}}))', "{{name}}")],
         unit="percent", w=12, thresh=40, h=9, desc="Containers without a CPU quota are omitted. Red line = alert threshold.")
    d.ts("Container memory % of limit", [('100 * container_memory_working_set_bytes{name=~"$container"} / (container_spec_memory_limit_bytes{name=~"$container"} > 0)', "{{name}}")],
         unit="percent", w=12, thresh=40, h=9, desc="Working set / memory limit. Red line = alert threshold.")
    d.ts("Container CPU (cores)", [(cpu, "{{name}}")], unit="short", w=8)
    d.ts("Container memory (working set)", [('container_memory_working_set_bytes{name=~"$container"}', "{{name}}")], unit="bytes", w=8)
    d.ts("Container network I/O", [('sum by (name) (rate(container_network_receive_bytes_total{name=~"$container"}[$__rate_interval]))', "rx {{name}}"),
        ('-sum by (name) (rate(container_network_transmit_bytes_total{name=~"$container"}[$__rate_interval]))', "tx {{name}}")], unit="Bps", w=8)

    d.row("Nodes (host)")
    d.gauge("Node CPU %", '100 - avg(rate(node_cpu_seconds_total{mode="idle"}[$__rate_interval])) * 100', steps=[{"color": GREEN, "value": None}, {"color": RED, "value": 40}], w=4)
    d.gauge("Node memory %", '100 * (1 - node_memory_MemAvailable_bytes / node_memory_MemTotal_bytes)', steps=[{"color": GREEN, "value": None}, {"color": RED, "value": 40}], w=4)
    d.gauge("Root disk used %", '100 * (1 - node_filesystem_avail_bytes{mountpoint="/"} / node_filesystem_size_bytes{mountpoint="/"})', steps=[{"color": GREEN, "value": None}, {"color": AMBER, "value": 70}, {"color": RED, "value": 85}], w=4)
    d.ts("Node CPU by mode", [('sum by (mode) (rate(node_cpu_seconds_total{mode!="idle"}[$__rate_interval])) / count(node_cpu_seconds_total{mode="idle"}) * 100', "{{mode}}")], unit="percent", w=12, stack=True)
    d.ts("Node load", [('node_load1', "1m"), ('node_load5', "5m"), ('node_load15', "15m")], w=6)
    d.ts("Node memory", [('node_memory_MemTotal_bytes - node_memory_MemAvailable_bytes', "used"), ('node_memory_MemAvailable_bytes', "available")], unit="bytes", w=6)
    d.ts("Node network", [('sum(rate(node_network_receive_bytes_total{device!~"lo|veth.*|br-.*|docker.*"}[$__rate_interval]))', "rx"),
        ('-sum(rate(node_network_transmit_bytes_total{device!~"lo|veth.*|br-.*|docker.*"}[$__rate_interval]))', "tx")], unit="Bps", w=8)
    d.ts("Node disk I/O", [('sum(rate(node_disk_read_bytes_total[$__rate_interval]))', "read"), ('-sum(rate(node_disk_written_bytes_total[$__rate_interval]))', "write")], unit="Bps", w=8)
    d.ts("Node disk space", [('node_filesystem_size_bytes{mountpoint="/"} - node_filesystem_avail_bytes{mountpoint="/"}', "used"), ('node_filesystem_avail_bytes{mountpoint="/"}', "free")], unit="bytes", w=8)

    d.row("Frontend (nginx)")
    d.ts("nginx requests/s", [('rate(nginx_requests[$__rate_interval])', "requests")], unit="reqps", w=8)
    d.ts("nginx connections", [('nginx_connections_current', "{{state}}")], w=8)
    d.ts("nginx accepted vs handled", [('rate(nginx_connections_accepted[$__rate_interval])', "accepted"), ('rate(nginx_connections_handled[$__rate_interval])', "handled")], w=8)

    d.row("Logs & traces")
    d.logs("Logs (all services, level filter)", '{service_name=~".+"} | detected_level=~"(?i)$level"')
    d.nodegraph("Service map")
    d.traces_table("Recent traces")
    return d


# ----------------------------------------------------------------------------- kafka
def kafka():
    d = Dash("linkedin-kafka", "Kafka", "Broker health, throughput, consumer lag and latency (JMX exporter + kafka-exporter + cAdvisor).", ["kafka"])
    d.row("Broker status")
    d.stat("Broker status", 'up{job="kafka-jmx"}', mappings=[{"type": "value", "options": {"0": {"text": "DOWN", "color": RED}, "1": {"text": "UP", "color": GREEN}}}],
           steps=[{"color": RED, "value": None}, {"color": GREEN, "value": 1}], desc="JMX exporter scrape health.")
    d.stat("Brokers in cluster", 'kafka_brokers', steps=[{"color": RED, "value": None}, {"color": GREEN, "value": 1}], desc="kafka-exporter metadata.")
    d.stat("Broker state", 'max(kafka_server_kafkaserver_brokerstate)', desc="3 = running.", steps=[{"color": RED, "value": None}, {"color": GREEN, "value": 3}])
    d.stat("Active controllers", 'sum(kafka_controller_kafkacontroller_activecontrollercount)', steps=[{"color": RED, "value": None}, {"color": GREEN, "value": 1}])
    d.stat("Under-replicated partitions", 'sum(kafka_server_replicamanager_underreplicatedpartitions)', steps=[{"color": GREEN, "value": None}, {"color": RED, "value": 1}])
    d.stat("Offline partitions", 'sum(kafka_controller_kafkacontroller_offlinepartitionscount)', steps=[{"color": GREEN, "value": None}, {"color": RED, "value": 1}])
    d.ts("Under-replicated / offline / under-min-ISR", [('sum(kafka_server_replicamanager_underreplicatedpartitions)', "under-replicated"),
        ('sum(kafka_controller_kafkacontroller_offlinepartitionscount)', "offline"), ('sum(kafka_server_replicamanager_underminisrpartitioncount)', "under min ISR")], w=12)
    d.ts("Partitions & topics", [('sum(kafka_server_replicamanager_partitioncount)', "partitions"), ('sum(kafka_server_replicamanager_leadercount)', "leaders"),
        ('sum(kafka_controller_kafkacontroller_globaltopiccount)', "topics")], w=12)
    d.row("Throughput")
    d.ts("Messages in / sec", [('sum(rate(kafka_server_brokertopicmetrics_messagesinpersec_total[$__rate_interval]))', "messages in")], unit="ops", w=8)
    d.ts("Bytes in / out per sec", [('sum(rate(kafka_server_brokertopicmetrics_bytesinpersec_total[$__rate_interval]))', "bytes in"),
        ('-sum(rate(kafka_server_brokertopicmetrics_bytesoutpersec_total[$__rate_interval]))', "bytes out")], unit="Bps", w=8)
    d.ts("Produce / fetch requests per sec", [('sum(rate(kafka_server_brokertopicmetrics_totalproducerequestspersec_total[$__rate_interval]))', "produce"),
        ('sum(rate(kafka_server_brokertopicmetrics_totalfetchrequestspersec_total[$__rate_interval]))', "fetch"),
        ('sum(rate(kafka_server_brokertopicmetrics_failedproducerequestspersec_total[$__rate_interval]))', "failed produce"),
        ('sum(rate(kafka_server_brokertopicmetrics_failedfetchrequestspersec_total[$__rate_interval]))', "failed fetch")], unit="reqps", w=8)
    d.ts("Messages in per topic (offset rate)", [('sum by (topic) (rate(kafka_topic_partition_current_offset[$__rate_interval]))', "{{topic}}")], unit="ops", w=12, desc="From kafka-exporter.")
    d.ts("Log size per topic", [('sum by (topic) (kafka_log_log_size)', "{{topic}}")], unit="bytes", w=12)
    d.row("Consumer lag")
    d.stat("Total consumer lag", 'sum(kafka_consumergroup_lag)', steps=[{"color": GREEN, "value": None}, {"color": AMBER, "value": 100}, {"color": RED, "value": 1000}], w=4, h=8)
    d.ts("Consumer lag by group / topic", [('sum by (consumergroup, topic) (kafka_consumergroup_lag)', "{{consumergroup}} · {{topic}}")], w=20, h=8)
    d.row("Latency")
    d.ts("Produce latency (TotalTimeMs)", [('kafka_network_requestmetrics_totaltimems{request="Produce",quantile=~"50thPercentile|95thPercentile|99thPercentile"}', "{{quantile}}")], unit="ms", w=12)
    d.ts("Fetch latency (TotalTimeMs, consumers)", [('kafka_network_requestmetrics_totaltimems{request="FetchConsumer",quantile=~"50thPercentile|95thPercentile|99thPercentile"}', "{{quantile}}")], unit="ms", w=12)
    d.ts("Request queue / network idle", [('kafka_network_requestchannel_requestqueuesize', "request queue"), ('kafka_network_socketserver_networkprocessoravgidlepercent*100', "network idle %")], w=12)
    d.ts("ISR shrinks / expands per sec", [('rate(kafka_server_replicamanager_isrshrinkspersec_total[$__rate_interval])', "shrinks"), ('rate(kafka_server_replicamanager_isrexpandspersec_total[$__rate_interval])', "expands")], w=12)
    d.row("Resources (container `kafka`)")
    d.ts("CPU", [('sum(rate(container_cpu_usage_seconds_total{name="kafka"}[$__rate_interval])) * 100', "container CPU % of 1 core"), ('kafka_jvm_os_processcpuload * 100', "JVM process CPU %")], unit="percent", w=6, thresh=40)
    d.ts("Memory", [('container_memory_working_set_bytes{name="kafka"}', "working set"), ('container_spec_memory_limit_bytes{name="kafka"}', "limit"), ('jvm_memory_usage_heap_used', "JVM heap used")], unit="bytes", w=6)
    d.ts("Disk", [('sum(kafka_log_log_size)', "log size"), ('sum(rate(container_fs_writes_bytes_total{name="kafka"}[$__rate_interval]))', "disk write B/s"), ('sum(rate(container_fs_reads_bytes_total{name="kafka"}[$__rate_interval]))', "disk read B/s")], unit="bytes", w=6)
    d.ts("Network", [('sum(rate(container_network_receive_bytes_total{name="kafka"}[$__rate_interval]))', "rx"), ('-sum(rate(container_network_transmit_bytes_total{name="kafka"}[$__rate_interval]))', "tx")], unit="Bps", w=6)
    return d


# ----------------------------------------------------------------------------- mysql
def mysql():
    d = Dash("linkedin-mysql", "MySQL", "MySQL primary/replica health, connections, queries, locks, cache and size (mysqld_exporter v0.16).", ["mysql"],
             [{"name": "job", "label": "Instance", "type": "custom", "multi": False, "includeAll": False,
               "query": "mysql-primary,mysql-replica", "current": {"text": "mysql-primary", "value": "mysql-primary"}, "options": []}])
    J = 'job="$job"'
    d.row("Status")
    d.stat("Database status", f'mysql_up{{{J}}}', mappings=[{"type": "value", "options": {"0": {"text": "DOWN", "color": RED}, "1": {"text": "UP", "color": GREEN}}}], steps=[{"color": RED, "value": None}, {"color": GREEN, "value": 1}])
    d.stat("Uptime", f'mysql_global_status_uptime{{{J}}}', unit="s", graph=False)
    d.stat("Replica IO / SQL running", 'mysql_slave_status_slave_io_running{job="mysql-replica"} * mysql_slave_status_slave_sql_running{job="mysql-replica"}',
           mappings=[{"type": "value", "options": {"0": {"text": "BROKEN", "color": RED}, "1": {"text": "RUNNING", "color": GREEN}}}], steps=[{"color": RED, "value": None}, {"color": GREEN, "value": 1}], desc="Replica only.")
    d.stat("Replication lag", 'mysql_slave_status_seconds_behind_master{job="mysql-replica"}', unit="s", steps=[{"color": GREEN, "value": None}, {"color": AMBER, "value": 5}, {"color": RED, "value": 30}])
    d.stat("Read-only", f'mysql_global_variables_read_only{{{J}}}', mappings=[{"type": "value", "options": {"0": {"text": "read/write"}, "1": {"text": "read-only"}}}], graph=False)
    d.stat("Slow queries", f'increase(mysql_global_status_slow_queries{{{J}}}[$__range])', steps=[{"color": GREEN, "value": None}, {"color": AMBER, "value": 1}])
    d.ts("Replication lag over time", [('mysql_slave_status_seconds_behind_master{job="mysql-replica"}', "seconds behind")], unit="s", w=12)
    d.ts("Aborted connections / clients", [(f'rate(mysql_global_status_aborted_connects{{{J}}}[$__rate_interval])', "aborted connects"), (f'rate(mysql_global_status_aborted_clients{{{J}}}[$__rate_interval])', "aborted clients")], w=12)
    d.row("Connections & throughput")
    d.ts("Connections vs max", [(f'mysql_global_status_threads_connected{{{J}}}', "connected"), (f'mysql_global_status_threads_running{{{J}}}', "running"),
        (f'mysql_global_variables_max_connections{{{J}}}', "max_connections")], w=8)
    d.gauge("Connection usage %", f'100 * mysql_global_status_threads_connected{{{J}}} / mysql_global_variables_max_connections{{{J}}}', steps=[{"color": GREEN, "value": None}, {"color": AMBER, "value": 70}, {"color": RED, "value": 90}], w=4, h=8)
    d.ts("Queries / sec", [(f'rate(mysql_global_status_queries{{{J}}}[$__rate_interval])', "queries"), (f'sum(rate(mysql_global_status_commands_total{{{J},command=~"select|insert|update|delete"}}[$__rate_interval])) by (command)', "{{command}}")], unit="ops", w=12)
    d.ts("Commits / rollbacks per sec", [(f'rate(mysql_global_status_commands_total{{{J},command="commit"}}[$__rate_interval])', "commit"), (f'rate(mysql_global_status_commands_total{{{J},command="rollback"}}[$__rate_interval])', "rollback")], unit="ops", w=12)
    d.ts("Network bytes", [(f'rate(mysql_global_status_bytes_received{{{J}}}[$__rate_interval])', "received"), (f'-rate(mysql_global_status_bytes_sent{{{J}}}[$__rate_interval])', "sent")], unit="Bps", w=12)
    d.row("Query latency")
    d.ts("Avg statement latency (perf_schema)", [(f'sum(rate(mysql_perf_schema_events_statements_sum_timer_wait{{{J}}}[$__rate_interval])) / 1e12 / sum(rate(mysql_global_status_queries{{{J}}}[$__rate_interval]))', "avg s/query")],
         unit="s", w=12, desc="Total statement wait time per query (events_statements_summary_global_by_event_name).")
    d.ts("Statement time by type (s/s)", [(f'topk(6, sum by (event_name)(rate(mysql_perf_schema_events_statements_seconds_total{{{J}}}[$__rate_interval])))', "{{event_name}}")], unit="s", w=12,
         desc="Name of this collector's metric differs per exporter version; if empty check mysql_perf_schema_events_statements_*.")
    d.row("Locks & InnoDB")
    d.ts("Row lock waits / time", [(f'rate(mysql_global_status_innodb_row_lock_waits{{{J}}}[$__rate_interval])', "row lock waits/s"), (f'rate(mysql_global_status_innodb_row_lock_time{{{J}}}[$__rate_interval])/1000', "lock time s/s"),
        (f'mysql_global_status_innodb_row_lock_current_waits{{{J}}}', "current waits")], w=8)
    d.ts("Deadlocks", [(f'rate(mysql_info_schema_innodb_metrics_lock_lock_deadlocks_total{{{J}}}[$__rate_interval])', "deadlocks/s"), (f'increase(mysql_info_schema_innodb_metrics_lock_lock_deadlocks_total{{{J}}}[$__rate_interval])', "deadlocks")], w=8)
    d.ts("Table locks", [(f'rate(mysql_global_status_table_locks_waited{{{J}}}[$__rate_interval])', "waited"), (f'rate(mysql_global_status_table_locks_immediate{{{J}}}[$__rate_interval])', "immediate")], w=8)
    d.gauge("InnoDB buffer pool hit ratio", f'100 * (1 - rate(mysql_global_status_innodb_buffer_pool_reads{{{J}}}[$__rate_interval]) / rate(mysql_global_status_innodb_buffer_pool_read_requests{{{J}}}[$__rate_interval]))',
            steps=[{"color": RED, "value": None}, {"color": AMBER, "value": 95}, {"color": GREEN, "value": 99}], w=6, h=8)
    d.ts("Cache hit ratio over time", [(f'100 * (1 - rate(mysql_global_status_innodb_buffer_pool_reads{{{J}}}[$__rate_interval]) / rate(mysql_global_status_innodb_buffer_pool_read_requests{{{J}}}[$__rate_interval]))', "buffer pool hit %")], unit="percent", w=9, h=8, mx=100)
    d.ts("Buffer pool pages", [(f'mysql_global_status_buffer_pool_pages{{{J}}}', "{{state}}")], w=9, h=8)
    d.row("Size & disk")
    d.bars("Database size by schema", [(f'sum by (schema) (mysql_info_schema_table_size{{{J},schema=~"user_db|post_db"}})', "{{schema}}")], unit="bytes", w=8)
    d.ts("Data vs index size", [(f'sum by (component) (mysql_info_schema_table_size{{{J},schema=~"user_db|post_db"}})', "{{component}}")], unit="bytes", w=8, stack=True)
    d.ts("Binlog size", [(f'mysql_binlog_size_bytes{{{J}}}', "binlog bytes")], unit="bytes", w=8)
    d.ts("Container disk I/O", [('sum(rate(container_fs_writes_bytes_total{name=~"$job"}[$__rate_interval]))', "write"), ('sum(rate(container_fs_reads_bytes_total{name=~"$job"}[$__rate_interval]))', "read")], unit="Bps", w=8)
    d.ts("Container CPU / memory", [('sum(rate(container_cpu_usage_seconds_total{name=~"$job"}[$__rate_interval])) * 100', "CPU % of 1 core"), ], unit="percent", w=8, thresh=40)
    d.ts("Container memory", [('container_memory_working_set_bytes{name=~"$job"}', "working set"), ('container_spec_memory_limit_bytes{name=~"$job"}', "limit")], unit="bytes", w=8)
    d.ts("Node disk used", [('node_filesystem_size_bytes{mountpoint="/"} - node_filesystem_avail_bytes{mountpoint="/"}', "used")], unit="bytes", w=8)
    return d


# ----------------------------------------------------------------------------- proxysql
def proxysql():
    d = Dash("linkedin-proxysql", "ProxySQL", "Pooling and read/write splitting. Metrics come from SQL against ProxySQL's stats schema (collector sqlquery receiver).", ["mysql", "proxysql"])
    G = lambda v: f'proxysql_global{{variable_name="{v}"}}'
    d.text("Notes", "Counters (`Questions`, `Com_*`, ...) are cumulative values stored as gauges -> use `rate()`. Hostgroup **10** = writer (primary), **20** = readers (replica + primary fail-safe). "
           "**Waiting clients** has no native ProxySQL statistic: the panel is an *approximation* (idle client sessions and pool `get_conn_failure` rate).", h=3)
    d.row("Status & connections")
    d.stat("Status", 'clamp_max(count(proxysql_global{variable_name="ProxySQL_Uptime"}) , 1)', mappings=[{"type": "value", "options": {"0": {"text": "DOWN", "color": RED}, "1": {"text": "UP", "color": GREEN}}}],
           steps=[{"color": RED, "value": None}, {"color": GREEN, "value": 1}], desc="Series exists = collector can query the admin interface (no data = DOWN).")
    d.stat("Uptime", G("ProxySQL_Uptime"), unit="s", graph=False)
    d.stat("Client connections", G("Client_Connections_connected"))
    d.stat("Active server connections", 'sum(proxysql_connpool_conn_used)')
    d.stat("Idle server connections", 'sum(proxysql_connpool_conn_free)')
    d.stat("Servers not ONLINE", 'count(proxysql_connpool_conn_used{status!="ONLINE"}) or vector(0)', steps=[{"color": GREEN, "value": None}, {"color": RED, "value": 1}])
    d.ts("Client connections", [(G("Client_Connections_connected"), "connected"), (G("Client_Connections_non_idle"), "non-idle"),
        (f'rate({G("Client_Connections_created")}[$__rate_interval])', "created/s"), (f'rate({G("Client_Connections_aborted")}[$__rate_interval])', "aborted/s")], w=12)
    d.ts("Waiting clients (approximation)", [(f'{G("Client_Connections_connected")} - {G("Client_Connections_non_idle")}', "idle client sessions"),
        (f'rate({G("ConnPool_get_conn_failure")}[$__rate_interval])', "pool get_conn failures/s (clients that had to wait/fail)"),
        (f'rate({G("ConnPool_get_conn_success")}[$__rate_interval]) - rate({G("ConnPool_get_conn_immediate")}[$__rate_interval])', "pool gets not served immediately/s")],
         w=12, desc="Approximation - ProxySQL does not expose a wait-queue length.")
    d.row("Backend pool")
    d.ts("Active vs idle server connections by server", [('sum by (hostgroup, srv_host) (proxysql_connpool_conn_used)', "used hg{{hostgroup}} {{srv_host}}"), ('sum by (hostgroup, srv_host) (proxysql_connpool_conn_free)', "free hg{{hostgroup}} {{srv_host}}")], w=12)
    d.ts("Pool utilisation %", [('100 * sum by (hostgroup, srv_host) (proxysql_connpool_conn_used) / clamp_min(sum by (hostgroup, srv_host) (proxysql_connpool_conn_used) + sum by (hostgroup, srv_host) (proxysql_connpool_conn_free), 1)', "hg{{hostgroup}} {{srv_host}}")],
         unit="percent", w=12, mx=100, thresh=80, desc="used / (used + free) of the connections currently open to each backend.")
    d.ts("Connection limits (max used vs configured 100)", [('max by (hostgroup, srv_host) (proxysql_connpool_max_conn_used)', "peak used hg{{hostgroup}} {{srv_host}}"), ('vector(100)', "max_connections (config: 100 per backend)")], w=12,
         desc="Configured limits live in docker/proxysql/proxysql.cnf.tmpl (100 per server, 20 for the primary reader fail-safe).")
    d.ts("Server connections created / aborted per sec", [(f'rate({G("Server_Connections_created")}[$__rate_interval])', "created"), (f'rate({G("Server_Connections_aborted")}[$__rate_interval])', "aborted"),
        (f'{G("Server_Connections_connected")}', "connected")], w=12)
    d.row("Throughput & latency")
    d.ts("Queries / sec", [(f'rate({G("Questions")}[$__rate_interval])', "queries"), (f'rate({G("Slow_queries")}[$__rate_interval])', "slow")], unit="ops", w=8)
    d.ts("Transactions / sec (commit + rollback)", [(f'rate({G("Com_commit")}[$__rate_interval])', "commit"), (f'rate({G("Com_rollback")}[$__rate_interval])', "rollback"), (f'{G("Active_Transactions")}', "active transactions")], unit="ops", w=8)
    d.ts("Queries per backend / sec", [('sum by (hostgroup, srv_host) (rate(proxysql_connpool_queries[$__rate_interval]))', "hg{{hostgroup}} {{srv_host}}")], unit="ops", w=8)
    d.ts("Query & transaction latency (avg per command)", [('sum by (command) (rate(proxysql_commands_time_us[$__rate_interval])) / sum by (command) (rate(proxysql_commands_count[$__rate_interval])) / 1e6', "{{command}}")], unit="s", w=12,
         desc="Average time per command type from stats_mysql_commands_counters; COMMIT/ROLLBACK/BEGIN are the transaction latencies.")
    d.ts("Backend ping latency", [('max by (hostgroup, srv_host) (proxysql_connpool_latency_us) / 1e6', "hg{{hostgroup}} {{srv_host}}")], unit="s", w=12)
    d.row("Errors")
    d.ts("Connection errors", [('sum by (hostgroup, srv_host) (rate(proxysql_connpool_conn_err[$__rate_interval]))', "backend conn errors hg{{hostgroup}} {{srv_host}}"),
        (f'rate({G("ConnPool_get_conn_failure")}[$__rate_interval])', "pool get_conn failures"), (f'rate({G("Client_Connections_aborted")}[$__rate_interval])', "client aborted")], w=12)
    d.ts("Server login failures / access denied", [(f'rate({G("Server_Connections_aborted")}[$__rate_interval])', "server connections aborted"),
        (f'rate({G("Access_Denied_Wrong_Password")}[$__rate_interval])', "access denied: wrong password"), (f'rate({G("Access_Denied_Max_Connections")}[$__rate_interval])', "denied: max connections"),
        (f'rate({G("Access_Denied_Max_User_Connections")}[$__rate_interval])', "denied: max user connections")], w=12)
    d.ts("Frontend / backend traffic", [(f'rate({G("Queries_frontends_bytes_recv")}[$__rate_interval])', "from clients"), (f'rate({G("Queries_backends_bytes_sent")}[$__rate_interval])', "to MySQL"),
        (f'rate({G("Queries_backends_bytes_recv")}[$__rate_interval])', "from MySQL")], unit="Bps", w=12)
    d.ts("Container CPU / memory", [('sum(rate(container_cpu_usage_seconds_total{name="proxysql"}[$__rate_interval])) * 100', "CPU % of 1 core"), ('container_memory_working_set_bytes{name="proxysql"} / 1e6', "memory MB")], w=12)
    return d


# ----------------------------------------------------------------------------- redis
def redis():
    d = Dash("linkedin-redis", "Redis", "Feed and notification store: health, memory, throughput, hit rate, persistence (oliver006/redis_exporter).", ["redis"])
    d.row("Status")
    d.stat("Redis status", 'redis_up', mappings=[{"type": "value", "options": {"0": {"text": "DOWN", "color": RED}, "1": {"text": "UP", "color": GREEN}}}], steps=[{"color": RED, "value": None}, {"color": GREEN, "value": 1}])
    d.stat("Uptime", 'redis_uptime_in_seconds', unit="s", graph=False)
    d.stat("Connected clients", 'redis_connected_clients')
    d.stat("Blocked clients", 'redis_blocked_clients', steps=[{"color": GREEN, "value": None}, {"color": AMBER, "value": 1}])
    d.stat("Memory used", 'redis_memory_used_bytes', unit="bytes")
    d.stat("Hit rate", '100 * rate(redis_keyspace_hits_total[$__range]) / (rate(redis_keyspace_hits_total[$__range]) + rate(redis_keyspace_misses_total[$__range]))', unit="percent", steps=[{"color": RED, "value": None}, {"color": AMBER, "value": 50}, {"color": GREEN, "value": 80}], decimals=1)
    d.row("Throughput")
    d.ts("Commands / sec", [('rate(redis_commands_processed_total[$__rate_interval])', "commands")], unit="ops", w=8)
    d.ts("Commands by type (top 8)", [('topk(8, sum by (cmd) (rate(redis_commands_total[$__rate_interval])))', "{{cmd}}")], unit="ops", w=8)
    d.ts("Command latency (avg per call)", [('topk(8, sum by (cmd) (rate(redis_commands_duration_seconds_total[$__rate_interval])) / sum by (cmd) (rate(redis_commands_total[$__rate_interval])))', "{{cmd}}")], unit="s", w=8)
    d.ts("Keyspace hits / misses per sec", [('rate(redis_keyspace_hits_total[$__rate_interval])', "hits"), ('rate(redis_keyspace_misses_total[$__rate_interval])', "misses")], unit="ops", w=8)
    d.ts("Hit rate %", [('100 * rate(redis_keyspace_hits_total[$__rate_interval]) / (rate(redis_keyspace_hits_total[$__rate_interval]) + rate(redis_keyspace_misses_total[$__rate_interval]))', "hit rate")], unit="percent", w=8, mx=100)
    d.ts("Network in / out", [('rate(redis_net_input_bytes_total[$__rate_interval])', "in"), ('-rate(redis_net_output_bytes_total[$__rate_interval])', "out")], unit="Bps", w=8)
    d.row("Memory & keys")
    d.ts("Memory used vs max", [('redis_memory_used_bytes', "used"), ('redis_memory_max_bytes > 0', "maxmemory"), ('redis_memory_used_rss_bytes', "RSS")], unit="bytes", w=8)
    d.ts("Memory fragmentation ratio", [('redis_mem_fragmentation_ratio', "fragmentation")], w=8, desc=">1.5 = fragmented, <1 = swapping.")
    d.ts("Keys per database", [('sum by (db) (redis_db_keys)', "{{db}} keys"), ('sum by (db) (redis_db_keys_expiring)', "{{db}} expiring")], w=8)
    d.ts("Evicted / expired keys per sec", [('rate(redis_evicted_keys_total[$__rate_interval])', "evicted"), ('rate(redis_expired_keys_total[$__rate_interval])', "expired")], unit="ops", w=12)
    d.ts("Connections", [('redis_connected_clients', "connected"), ('rate(redis_connections_received_total[$__rate_interval])', "new/s"), ('rate(redis_rejected_connections_total[$__rate_interval])', "rejected/s")], w=12)
    d.row("Persistence, replication & CPU")
    d.ts("Time since last RDB save", [('time() - redis_rdb_last_save_timestamp_seconds', "seconds")], unit="s", w=6)
    d.ts("Persistence status", [('redis_rdb_last_bgsave_status', "RDB last bgsave ok"), ('redis_aof_enabled', "AOF enabled"), ('redis_aof_last_write_status', "AOF last write ok"), ('redis_rdb_changes_since_last_save', "changes since save")], w=6)
    d.ts("Replication", [('redis_connected_slaves', "connected replicas"), ('redis_master_repl_offset', "master repl offset")], w=6)
    d.ts("Slowlog length", [('redis_slowlog_length', "entries")], w=6)
    d.ts("Redis CPU (s/s)", [('rate(redis_cpu_sys_seconds_total[$__rate_interval])', "sys"), ('rate(redis_cpu_user_seconds_total[$__rate_interval])', "user")], w=8)
    d.ts("Container CPU / memory", [('sum(rate(container_cpu_usage_seconds_total{name="redis"}[$__rate_interval])) * 100', "CPU % of 1 core"), ('100 * container_memory_working_set_bytes{name="redis"} / (container_spec_memory_limit_bytes{name="redis"} > 0)', "memory % of limit")], unit="percent", w=8, thresh=40)
    d.ts("Latest fork duration", [('redis_latest_fork_seconds', "fork")], unit="s", w=8)
    return d


if __name__ == "__main__":
    for fn in (services, kafka, mysql, proxysql, redis):
        d = save(fn())
        print(d.uid, len([p for p in d.panels if p["type"] != "row"]), "panels")
