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

The Kafka consumer lag panels rely on the Kafka client's built-in
Micrometer metrics binder (auto-registered by spring-kafka once a
`MeterRegistry` bean exists — no extra dependency needed) — the exact
label set can shift slightly across Kafka client versions, so if that
panel comes up empty, check Prometheus's own UI
(http://localhost:9090/graph) for the real metric/label names and adjust
the query in `grafana/dashboards/oms-overview.json`.

## Editing the dashboard

Easiest path: edit it in the Grafana UI, then *Dashboard settings → JSON
Model*, copy the JSON back into `grafana/dashboards/oms-overview.json`.
Grafana polls this folder every 30s (`updateIntervalSeconds` in
`dashboards.yml`) and reloads on change, so no restart needed to see edits
made directly to the file either.

If you also run the Kubernetes stack (`k8s/10-grafana-dashboard.yaml`),
that file embeds a copy of this same JSON — keep them in sync rather than
editing them independently.
