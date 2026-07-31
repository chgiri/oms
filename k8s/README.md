# OMS on Kubernetes: web/worker split

This directory is the deployment-side half of `app.process.role` (see
[`docs/process-roles.md`](../docs/process-roles.md)). `docker-compose.yml`'s
`app` / `app-worker` split proves the mechanism works locally; it isn't a
production scaling artifact by itself — nothing there lets web and worker
scale independently under real load, on real signals, unattended. These
manifests are that piece: two `Deployment`s built from the same image, one
`web` behind a `Service` with an `HorizontalPodAutoscaler`, one `worker`
with no `Service` at all, scaled by KEDA on Kafka consumer lag and outbox
queue depth instead.

> **The external entry point now lives in `oms-gateway/k8s/`, not here.**
> `web`'s `Service` (`03-service-web.yaml`) is still what the gateway routes
> to in-cluster; it's only the `Ingress` that moved — see
> `oms-gateway/k8s/README.md` and the note at the top of `04-ingress.yaml`.

## Files

| File | What it is |
|---|---|
| `00-configmap.yaml` | Non-secret env vars shared by both roles |
| `01-secret.example.yaml` | **Template only** — copy it, fill in real values out of band, don't apply as-is |
| `02-deployment-web.yaml` | `APP_PROCESS_ROLE=web` — HTTP API only, no consumers/poller |
| `03-service-web.yaml` | ClusterIP in front of the web pods only |
| `04-ingress.yaml` | **Superseded** — see note below; kept for reference, not applied |
| `05-hpa-web.yaml` | Scales web on CPU utilization |
| `06-deployment-worker.yaml` | `APP_PROCESS_ROLE=worker` — consumers + outbox poller, no Service |
| `07-scaledobject-worker.yaml` | KEDA: scales worker on Kafka consumer lag + outbox depth |
| `08-pdb.yaml` | PodDisruptionBudgets for both roles |
| `09-podmonitor.yaml` | Optional — Prometheus Operator scrape config for both roles (not in `kustomization.yaml` by default; see below) |
| `10-grafana-dashboard.yaml` | Optional — ConfigMap that auto-imports the OMS Overview dashboard into Grafana via kube-prometheus-stack's sidecar (not in `kustomization.yaml` by default; see below) |
| `11-grafana-loki-datasource.yaml` | Optional — ConfigMap that auto-registers Loki as a Grafana datasource, same sidecar mechanism (not in `kustomization.yaml` by default; see below) |
| `kustomization.yaml` | Ties it all together; `kustomize edit set image` to point at your build |

## Prerequisites

- **metrics-server** — required for `05-hpa-web.yaml` (CPU-based HPA). Most
  managed clusters (EKS, GKE, AKS) already run this.
- **[KEDA](https://keda.sh)** — required for `07-scaledobject-worker.yaml`.
  Install it (Helm chart or operator) before applying that file, or the
  `ScaledObject`/`TriggerAuthentication` CRDs won't exist.
- **An ingress controller** — needed for `oms-gateway/k8s/04-ingress.yaml`
  now, not this directory's (see the note below). That one assumes
  `ingress-nginx`; swap `ingressClassName` and annotations for whatever you
  run.
- **Postgres, Kafka, Redis reachable from the cluster** — `00-configmap.yaml`
  points at `postgres` / `kafka` / `redis` as in-cluster Service names by
  default. Point these at your real managed instances if you're not
  running them in-cluster.

## Applying

```bash
# 1. Copy and fill in real secrets — never apply 01-secret.example.yaml directly
cp 01-secret.example.yaml 01-secret.yaml
# edit 01-secret.yaml with real values, then either:
kubectl apply -f 01-secret.yaml
# ...or add it to kustomization.yaml's resources list once filled in.

# 2. Point the image at your real build
kustomize edit set image your-registry.example.com/oms=your-registry.example.com/oms:$GIT_SHA

# 3. Apply everything else
kubectl apply -k .
```

## What scales on what

- **`oms-web`**: CPU utilization via a standard HPA (`05-hpa-web.yaml`),
  `minReplicas: 2` / `maxReplicas: 10`. Request-rate-based scaling is a
  valid alternative but needs Prometheus metrics this app doesn't expose
  yet — see the note at the top of `05-hpa-web.yaml` for exactly what that
  would take.
- **`oms-worker`**: KEDA, `minReplicaCount: 1` / `maxReplicaCount: 8`, on
  whichever of 3 triggers asks for the most replicas:
  - Consumer lag on each of the 2 Kafka consumer groups
    (`oms-inventory-service`, `oms-order-service`) on the `oms.order.events`
    topic. `oms-shipment-service` used to be a third group here until Stage 5
    of the microservices-prep plan moved that consumer into shipment-service's
    own deployable — see `07-scaledobject-worker.yaml`'s comment.
  - Row count in `oms_messaging.outbox_events` where `status = 'PENDING'`,
    queried directly against Postgres.

  Never scales to zero — `docs/process-roles.md` notes that a `web`
  instance's `OutboxService.enqueue()` calls just write `PENDING` rows and
  rely on *some* worker being up to flush them; at zero workers those rows
  would sit indefinitely instead of just until the next scale-up.

## Metrics

Both Deployments expose a second container port, `metrics` (8081) —
`management.server.port` in `application.properties`. **All** actuator
endpoints live there, not just `/actuator/prometheus`: health and info moved
too, which is why the probes on both Deployments target `metrics`, not
`http`. That port is never part of `03-service-web.yaml` and never
referenced by `04-ingress.yaml`, so it's unreachable from outside the
cluster regardless of the `permitAll` rule `SecurityConfig` adds for
`/actuator/prometheus` — only something inside the cluster network (a
Prometheus pod) can reach it.

Two ways to scrape it, pick whichever matches your Prometheus setup:

- **Prometheus Operator**: apply `09-podmonitor.yaml` (add it to
  `kustomization.yaml`'s `resources`, or `kubectl apply -f` it separately —
  it's commented out by default since it needs the
  `monitoring.coreos.com/v1` CRDs installed, which not every cluster has).
- **Plain Prometheus** with `kubernetes_sd_configs` pod discovery: already
  covered by the `prometheus.io/scrape`, `prometheus.io/port`,
  `prometheus.io/path` annotations on both pod templates — nothing further
  to apply.

Every metric carries a `role` tag (`web` or `worker`, mirroring
`APP_PROCESS_ROLE`) and an `application` tag — see
`management.metrics.tags.*` in `application.properties` — so a single
Grafana dashboard or Prometheus query can split web from worker even though
both scrape targets come from the same image.

### Grafana

No Grafana manifests are hand-rolled here on purpose — once you're already
depending on Prometheus Operator CRDs for `09-podmonitor.yaml`, the
standard way to get Prometheus *and* Grafana together is the
[kube-prometheus-stack](https://github.com/prometheus-community/helm-charts/tree/main/charts/kube-prometheus-stack)
Helm chart, rather than assembling your own Grafana `Deployment`/`Service`/
`PersistentVolumeClaim`/RBAC by hand:

```bash
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update
helm install monitoring prometheus-community/kube-prometheus-stack
```

That gives you Prometheus, Alertmanager, and Grafana with sane defaults,
including a dashboard sidecar that watches for ConfigMaps labeled
`grafana_dashboard: "1"` and loads them into Grafana automatically — which
is what `10-grafana-dashboard.yaml` is. Once the chart and
`09-podmonitor.yaml` are both applied:

```bash
kubectl apply -f 09-podmonitor.yaml
kubectl apply -f 10-grafana-dashboard.yaml
```

the **OMS Overview** dashboard (web/worker split, HTTP latency, Kafka
consumer lag, outbox depth, JVM, DB pool) shows up in Grafana with no
further clicking. It's the same dashboard JSON
`monitoring/grafana/dashboards/oms-overview.json` provisions into the local
docker-compose Grafana — edit that file and re-run
`k8s/10-grafana-dashboard.yaml`'s generation step (see the comment at the
top of that file) rather than editing the two independently.

If your cluster's Grafana sidecar only watches its own release namespace
(`sidecar.dashboards.searchNamespace`), apply `10-grafana-dashboard.yaml`
into that namespace, or set `searchNamespace: ALL` in your Helm values.

### Logs (Loki)

Same reasoning as Grafana above: rather than hand-rolling Loki's
`StatefulSet`/object-storage config and a Promtail `DaemonSet` by hand, use
the [loki-stack](https://github.com/grafana/helm-charts/tree/main/charts/loki-stack)
chart, with its bundled Grafana turned off since kube-prometheus-stack's is
already in use:

```bash
helm repo add grafana https://grafana.github.io/helm-charts
helm repo update
helm install loki grafana/loki-stack --set grafana.enabled=false
```

This deploys Loki plus a Promtail `DaemonSet` that ships every pod's
stdout/stderr cluster-wide — no annotations or sidecars needed on the OMS
Deployments themselves, unlike metrics. Then, since kube-prometheus-stack's
Grafana datasource sidecar is off by default (unlike its dashboard
sidecar):

```bash
helm upgrade monitoring prometheus-community/kube-prometheus-stack \
  --reuse-values \
  --set grafana.sidecar.datasources.enabled=true

kubectl apply -f 11-grafana-loki-datasource.yaml
```

and Loki shows up as a Grafana datasource, with "Recent app logs" already
working against `{container=~"oms-app.*"}` — Loki's default Kubernetes
pipeline labels every pod's logs by pod/namespace/container regardless of
any app-specific config.

**"Log volume by level" will likely come up empty**, though: that panel
groups by the `level` label, which only exists because
`monitoring/promtail/promtail-config.yaml` adds a custom regex stage that
parses `logging.pattern.console`'s exact format (see that file's
comments). loki-stack's default Promtail `DaemonSet` doesn't include that
stage — you'd need to pass it in via the chart's `promtail.config` Helm
value (the same regex/match/labels stages, adjusted for
`kubernetes_sd_configs` discovery instead of `docker_sd_configs`) to get
it working in-cluster too. Not wired up here since it's cluster-specific
enough (namespace, label selectors) to need tuning to your actual
deployment rather than a one-size-fits-all default.

## What this doesn't change

No application code changes — this is purely the orchestrator-side piece.
`app.process.role` and the guarded components (the 3 `@KafkaListener`
consumers, `OutboxPublisher`) work exactly as documented in
`docs/process-roles.md`; these manifests just point `APP_PROCESS_ROLE` at
two independent `Deployment`s instead of one `docker-compose` service each,
and replace `--scale` with real autoscaling signals.

## Tuning before production use

Everything marked with a comment in the manifests is a starting point, not
a recommendation — in particular:

- `resources.requests`/`limits` on both Deployments (placeholder values)
- `lagThreshold` on the 3 Kafka triggers and `targetQueryValue` on the
  Postgres trigger (placeholder values — tune against real throughput and
  `KAFKA_NUM_PARTITIONS`)
- HPA `averageUtilization: 70` and KEDA `pollingInterval`/`cooldownPeriod`
- `maxReplicas`/`maxReplicaCount` ceilings on both
