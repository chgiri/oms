# Local monitoring stack: Prometheus + Grafana

`docker compose up` now also starts a Prometheus and a Grafana alongside
`app`/`app-worker` — nothing extra to run. For the Kubernetes equivalent
(kube-prometheus-stack + a dashboard ConfigMap), see
[`../k8s/README.md`](../k8s/README.md#grafana).

## What's here

| Path | What it is |
|---|---|
| `prometheus/prometheus.yml` | Scrape config — targets `app`'s and `app-worker`'s `/actuator/prometheus` |
| `grafana/provisioning/datasources/datasource.yml` | Auto-registers Prometheus as Grafana's datasource on startup |
| `grafana/provisioning/dashboards/dashboards.yml` | Tells Grafana to load every dashboard JSON in `grafana/dashboards/` |
| `grafana/dashboards/oms-overview.json` | The dashboard itself — see below |
| `loki/loki-config.yaml` | Loki server config — filesystem storage, single binary, local-dev only |
| `promtail/promtail-config.yaml` | Discovers every Docker container and ships its logs to Loki; parses the app's log line format for `oms-app*`/`oms-app-worker*` containers specifically |

## Using it

```bash
docker compose up -d
```

- **Grafana**: http://localhost:3000 — login `admin` / `${GRAFANA_ADMIN_PASSWORD:-admin}`
  (see `env.example`). The **OMS Overview** dashboard is already there under
  the OMS folder, no import step needed.
- **Prometheus**: http://localhost:9090 — useful for ad hoc PromQL, or to
  check *Status → Targets* if a panel in Grafana looks empty (confirms
  `app`/`app-worker` are actually being scraped and up).
- **Loki**: http://localhost:3100 — no real UI of its own; query it through
  Grafana (Explore, or the Logs panels on the dashboard) or `logcli`. Every
  container's stdout/stderr lands here via Promtail, not just the app's.

## Logs (Loki)

Promtail discovers every container on the local Docker daemon and ships
its logs to Loki — you don't need to add a new service to this list for
its logs to show up. Two ways to look at them:

- **Grafana → Explore**, datasource "Loki", e.g.:
  ```
  {container="oms-app"}
  {container=~"oms-app.*"} |= "ERROR"
  {container=~"oms-app.*"} |= "<a specific correlation id>"
  {container="oms-postgres"}
  ```
- **The dashboard's Logs row** — "Recent app logs" (raw tail, filterable
  via the panel's own query box) and "Log volume by level" (a quick eyeball
  for whether ERROR/WARN volume just spiked).

Only `oms-app*`/`oms-app-worker*` containers get the `level` label (from
`logging.pattern.console` in `application.properties`, parsed by the
`match`/`regex`/`labels` stages in `promtail/promtail-config.yaml`) —
other containers' logs are still in Loki and searchable by `container=`,
just without that extra structure, since their line formats aren't ours to
assume.

`correlation_id` is parsed out too but deliberately **not** a Loki label —
same cardinality reasoning as `OutboxMetrics.java` skipping a
per-error-message Prometheus tag. It's still fully searchable as plain
text with `|= "<the id>"`, just not something you filter on as a label
in the UI's label dropdown.

## OMS Overview dashboard

Covers what's already instrumented in the app (see
`management.metrics.*` in `application.properties` and
`OutboxMetrics.java` — nothing new was added to the app to build this):

- **Overview** — instance counts, request rate, 5xx rate, outbox pending
  depth, max Kafka consumer lag, all split by the `role` tag (`web` vs
  `worker`) where relevant.
- **HTTP — web role** — request rate by status, p50/p95/p99 latency, top
  endpoints by traffic.
- **Kafka & Outbox — worker role** — consumer lag per group, outbox
  pending depth, publish rate (success vs failed), publish duration p95.
- **JVM & DB pool — by role** — heap used/max, process CPU, GC pause rate,
  HikariCP active/idle/pending connections.
- **Logs** — log volume by level and a live tail, both scoped to
  `oms-app*`/`oms-app-worker*` containers (see the Logs section above).

The Kafka consumer lag panels rely on the Kafka client's built-in
Micrometer metrics binder (auto-registered by spring-kafka once a
`MeterRegistry` bean exists — no extra dependency needed) — the exact
label set can shift slightly across Kafka client versions, so if that
panel comes up empty, check Prometheus's own UI
(http://localhost:9090/graph) for the real metric/label names and adjust
the query in `grafana/dashboards/oms-overview.json`.

## Running the app from IntelliJ instead of Docker

Grafana and Prometheus don't need the app to be containerized — only
reachable. To point this stack at an app you're running from IntelliJ:

1. **Don't start the `app`/`app-worker` containers** (they'd fight your
   IntelliJ run config for port 8080/8081):
   ```bash
   docker compose up -d postgres kafka redis prometheus grafana
   ```
2. **In your IntelliJ run config**, set env vars so the app reaches the
   same infra containers via their published host ports (values below
   match `docker-compose.yml`'s defaults — adjust for your `.env`):
   ```
   DB_HOST=localhost
   DB_PORT=5432
   KAFKA_BOOTSTRAP_SERVERS=localhost:29092
   REDIS_HOST=localhost
   REDIS_PORT=6379
   ```
   (Plus whatever `.env` requires — `DB_USERNAME`/`DB_PASSWORD`, `JWT_SECRET`,
   etc.; same required vars as running it in Docker.)
3. **Run it.** Prometheus already has a job (`oms-host-intellij` in
   `prometheus/prometheus.yml`) targeting `host.docker.internal:8081` — the
   same actuator port, just reached from outside Docker. It shows up in
   Grafana under the `compose_service="host-intellij"` label instead of
   `app`/`app-worker`.
4. **Sanity check**: http://localhost:9090/targets — the `oms-host-intellij`
   job should flip from red/down to green/up within ~15s of the app
   starting. If it stays down, confirm the app actually bound
   `management.server.port` (8081) and that nothing else on your machine
   already owns that port.

`host.docker.internal` resolves out of the box on Docker Desktop
(Mac/Windows); on Linux, `extra_hosts: host-gateway` on the `prometheus`
service in `docker-compose.yml` is what makes it resolve there too.

**Metrics work this way; logs don't, by default.** Promtail discovers
containers via the Docker socket (`docker_sd_configs`), so an app running
directly in IntelliJ — not a container — never shows up in Loki. Its
console output is just wherever IntelliJ's Run window sends it. If you
want those logs in Grafana too, the practical option is running the app in
Docker instead (`docker compose up -d app`) rather than trying to get
Promtail to tail a non-container process.

## Editing the dashboard

Easiest path: edit it in the Grafana UI, then *Dashboard settings → JSON
Model*, copy the JSON back into `grafana/dashboards/oms-overview.json`.
Grafana polls this folder every 30s (`updateIntervalSeconds` in
`dashboards.yml`) and reloads on change, so no restart needed to see edits
made directly to the file either.

If you also run the Kubernetes stack (`k8s/10-grafana-dashboard.yaml`),
that file embeds a copy of this same JSON — keep them in sync rather than
editing them independently.
