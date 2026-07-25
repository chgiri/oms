# Process roles: splitting web and worker

Historically every OMS instance did everything: served HTTP traffic *and*
ran the 3 order-events Kafka consumers *and* polled the outbox table. That
meant "scale the app" only ever meant "scale everything" — an HTTP traffic
spike and a Kafka backlog both forced you to add replicas of the same
do-everything process, whether or not that's actually where the load was.

`app.process.role` splits that into two roles you can scale independently.

## The property

| Value | Behavior |
|---|---|
| unset | Everything runs — today's behavior, unchanged. Nothing to do if you don't care about this. |
| `web` | HTTP API only. The 3 `@KafkaListener` consumers and the outbox poller (`OutboxPublisher.publishPendingEvents`) don't start. |
| `worker` | The 3 consumers and the outbox poller run. Tomcat still starts too (see below) unless you opt out of it separately. |

Set it via the `APP_PROCESS_ROLE` environment variable (Spring's relaxed
binding maps it to `app.process.role` automatically — there's no key for it
in `application.properties`, see the comment there for why).

Guarded components:
- `OrderCreatedInventoryConsumer` (reserves stock on `OrderCreated`)
- `OrderSagaEventConsumer` (drives order status off inventory/payment outcomes)
- `OrderConfirmedShipmentConsumer` (auto-creates a shipment on `OrderConfirmed`)
- `OutboxPublisher` (the `@Scheduled` poller that flushes outbox rows to Kafka)

A `web` instance can still safely call `OutboxService.enqueue(...)` from an
HTTP request — that just writes a `PENDING` row. It relies on some `worker`
instance being up somewhere to actually publish it; the row sits there
(harmlessly) until one is.

## Running it locally

`docker-compose.yml` now has two app services:

- `app` — `APP_PROCESS_ROLE=web`, publishes port 8080, has the existing
  `oms-app` container name.
- `app-worker` — `APP_PROCESS_ROLE=worker`, no published port, no fixed
  container name (see below for why that matters).

```bash
docker compose up -d
# or, to run more worker capacity without touching `app`:
docker compose up -d --scale app-worker=3
```

### Why only `app-worker` can be `--scale`d here

Docker Compose's `--scale` needs to create N containers from one service
definition, which means that service can't pin a `container_name` (names
must be unique) or a static host port mapping (two containers can't both
bind host `8080`). `app-worker` has neither, so `--scale app-worker=N` works
as-is.

`app` still has both (`container_name: oms-app`, `ports: ["8080:8080"]`) —
kept because most people running this compose file locally want one
predictable URL (`localhost:8080`) and one predictable container name to
`docker logs`, not a scaled fleet on their laptop. If you do want to scale
`app` locally too, drop `container_name` and replace the static `ports`
mapping with a range (e.g. `"8080-8082:8080"`) or no host mapping at all and
put something in front of it.

### Real deployments

In Kubernetes (or any orchestrator with real load balancing), this maps
onto two `Deployment`s built from the same image — a `web` one behind a
Service/Ingress with an HPA on request rate or CPU, and a `worker` one with
no Service at all, scaled on consumer lag or outbox queue depth instead.
Neither the container-name nor the host-port constraint above applies
there — this compose setup's `app` limitation is a local-dev artifact, not
something the `app.process.role` property itself imposes.

## Optional: skip Tomcat entirely on workers

`app.process.role=worker` doesn't stop Tomcat from starting — it only
guards the consumers/poller. If you want a worker instance to skip the web
server too (pure resource savings, not required for correctness), set
`spring.main.web-application-type=none` (env: `SPRING_MAIN_WEB_APPLICATION_TYPE=none`)
alongside it.

Trade-off: `/actuator/health` is served over HTTP, so the moment the web
server never starts, the curl-based healthcheck in `docker-compose.yml`
has nothing to hit. If you take this option, swap `app-worker`'s
healthcheck for something that doesn't need an HTTP listener (e.g. a
simple process check) rather than leaving the curl one in place.
