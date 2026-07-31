# OMS — Order Management System

A domain-driven order management backend built with Spring Boot: customers,
products, inventory, orders, payments, and shipments, connected by an
event-driven saga instead of direct service-to-service calls.

**This repository is a monolith in the middle of being decomposed into
microservices** — see [Microservices extraction status](#microservices-extraction-status)
below before assuming any given module is still owned here.

## Features

- **Domain modules** — `auth`, `customer`, `product`, `inventory`, `order`,
  `payment`, `shipment`, each with its own controller/service/repository/DTO
  layers, plus `productclient`/`customerclient` (resilient HTTP clients for
  the two modules already extracted — see below).
- **Module boundaries enforced by Spring Modulith** — `ModularityTests`
  fails the build if any module reaches into another's `entity`/`repository`
  package directly instead of going through its public service interface.
  This isn't just documentation-as-comments: it's what made extracting
  Product/Customer/Shipment into standalone services mechanical rather than
  an archaeology project, and it's actively enforced for every module still
  in this repo.
- **Event-driven order flow** — a transactional outbox publishes domain
  events (`OrderCreated`, `PaymentConfirmed`, `ProductCreated`, ...) to
  Kafka; independent consumers reserve inventory, advance the order saga
  (`AWAITING_PAYMENT` / `CANCELLED`), and auto-create shipments once an
  order is confirmed. See [`docs/process-roles.md`](docs/process-roles.md).
  Every event carries a `schemaVersion` field and follows a documented
  additive-only compatibility policy — see
  [`docs/event-schema-versioning.md`](docs/event-schema-versioning.md) —
  since these events are now a real cross-deployable contract, not just an
  internal DTO.
- **Resilient service clients (`productclient`, `customerclient`)** —
  RestClient-based clients with Resilience4j circuit-breaker/retry/timeout,
  used by `OrderServiceImpl`/`InventoryServiceImpl` ahead of Stage 4's
  cutover (see below). Deliberately no fallback/stale-data path — a
  service being unreachable fails the calling request rather than silently
  serving stale data.
- **Distributed tracing (OpenTelemetry + Tempo)** — every HTTP request and
  Kafka produce/consume gets a span via Micrometer Tracing's OTel bridge,
  exported to Tempo and viewable in Grafana. Trace/span IDs are stamped into
  every log line alongside the existing per-request correlation ID (see
  `logging.pattern.console`), so a request can be followed both as a visual
  trace and as plain-text logs across every process it touched.
- **Independently scalable web/worker roles** — the same image runs as an
  API-only `web` process or a consumer-only `worker` process via
  `APP_PROCESS_ROLE`, so an HTTP traffic spike and a Kafka backlog scale
  separately instead of forcing you to add replicas of one do-everything
  process.
- **JWT auth (RS256)** — asymmetric signing with a public JWKS endpoint
  (`/.well-known/jwks.json`), so independent services (product-service,
  customer-service, shipment-service, oms-gateway, oms-bff) can all verify
  tokens without ever holding the signing key — this is already in active
  use, not just a future-proofing gesture.
- **Distributed locking & rate limiting** — Redisson-backed locks around
  concurrent inventory updates, and Bucket4j-backed login rate limiting, both
  correct across multiple app instances.
- **HashiCorp Vault** — secrets (DB/Redis passwords, JWT keys, admin
  password) are resolved from Vault rather than passed as plain env vars in
  uat/prod (Kubernetes auth) and, optionally, in local dev too. See
  [`vault/README.md`](vault/README.md).
- **Observability** — Prometheus metrics, Grafana dashboards, Loki/Promtail
  log aggregation, and Tempo distributed tracing, provisioned out of the box
  locally and via Kubernetes manifests for a real cluster.

## Microservices extraction status

This repo is executing a staged plan to pull Product, Customer, and Shipment
out into their own deployables. **As of now, none of the three have
finished cutting over** — oms-main still holds the authoritative
implementation and data for all three modules. Don't assume a module is
"done" just because a sibling service repo exists for it.

| Module | Sibling service repo | Scaffold + API contract (Stages 1-2) | Data cutover runbook (Stage 3) | Call sites swapped in oms-main (Stage 4) | Removed from oms-main (Stage 5) |
|---|---|---|---|---|---|
| Product | `product-service` | Done | [`docs/stage3-data-cutover-runbook-product.md`](docs/stage3-data-cutover-runbook-product.md) | Not yet | Not yet |
| Customer | `customer-service` | Done | [`docs/stage3-data-cutover-runbook-customer.md`](docs/stage3-data-cutover-runbook-customer.md) | Not yet | Not yet |
| Shipment | `shipment-service` | Done | [`docs/stage3-data-cutover-runbook-shipment.md`](docs/stage3-data-cutover-runbook-shipment.md) | N/A — see note | Not yet |

Practically, this means:

- `ProductClient`/`CustomerClient` (in `productclient`/`customerclient`)
  already exist and are fully built, but `OrderServiceImpl`/
  `InventoryServiceImpl` still call the in-process `ProductService`/
  `CustomerService` — the clients aren't wired in as the live path yet.
- The three `stage3-*` runbooks are the maintenance-window procedures for
  copying each module's data into its own service's database and (for
  Product/Customer) cutting `OrderServiceImpl` over to the client. Shipment
  has no equivalent Stage 4 deploy — nothing in oms-main calls
  `ShipmentService` synchronously, so there's no in-process call site to
  swap; shipment-service's own `OrderClient` already calls out to oms-main
  from Stage 2.
- Shipment has a wrinkle Product/Customer don't: `OrderConfirmedShipmentConsumer`
  auto-creates shipments off a Kafka event, not just REST — its cutover
  runbook has an extra step (stopping oms-main's consumer group membership
  entirely before shipment-service's own copy starts) that the Product/
  Customer runbooks don't need. Read that runbook's intro before assuming
  the pattern is identical.
- `app.product.writes-frozen` / `app.customer.writes-frozen` /
  `app.shipment.writes-frozen` (all default `false`) are the maintenance-window
  freeze flags each runbook uses — see `ShipmentWritesFrozenException` and
  its Product/Customer equivalents for what they guard.
- `ModularityTests` still treats `product`, `customer`, and `shipment` as
  regular internal modules — that only changes at Stage 5, once each
  module's code is physically deleted from this repo.

## Sibling services (separate repos)

Five services sit around oms-main and are **not** part of this repository —
all five are referenced from `docker-compose.yml` as sibling checkouts,
expected at `../<repo-name>` relative to this repo (`../oms-bff`,
`../oms-gateway`, `../product-service`, `../customer-service`,
`../shipment-service`), each with its own Dockerfile and README that this
one doesn't attempt to duplicate:

- **`oms-bff`** — a GraphQL BFF in front of oms-main, for frontend
  consumption patterns REST doesn't fit well (see `bruno/graphql/`).
- **`oms-gateway`** — a Spring Cloud Gateway sitting at the edge: JWT
  validation, Redis-backed rate limiting, and CORS, in front of oms-main.
- **`product-service`**, **`customer-service`**, **`shipment-service`** —
  the three Phase 4 extraction targets from the
  [Microservices extraction status](#microservices-extraction-status)
  table above. Each is a fully independent, already-deployable service;
  "sibling repo" here doesn't imply "finished cutting over" — see that
  table for what's actually done per module.

## Tech stack

| | |
|---|---|
| Language / runtime | Java 21, Spring Boot 4 |
| Persistence | PostgreSQL, Flyway migrations |
| Messaging | Apache Kafka (transactional outbox pattern) |
| Cache / locks / rate limiting | Redis, Redisson, Bucket4j |
| Auth | Spring Security, JWT (RS256) |
| Secrets | HashiCorp Vault |
| Service-to-service resilience | Resilience4j (circuit breaker, retry, timeout) |
| Module boundary enforcement | Spring Modulith |
| Distributed tracing | Micrometer Tracing + OpenTelemetry, Grafana Tempo |
| API docs | springdoc-openapi / Swagger UI |
| Build | Maven |
| Containerization | Docker, Docker Compose |
| Orchestration | Kubernetes (+ KEDA for worker autoscaling) |
| Monitoring | Prometheus, Grafana, Loki, Promtail, Tempo |
| Contract/integration testing | Testcontainers, WireMock |

## Getting started

### Prerequisites

- Docker & Docker Compose
- JDK 21 (only if running outside Docker, e.g. from an IDE)

### Run everything locally

```bash
cp env.example .env
# edit .env — at minimum set DB_PASSWORD, REDIS_PASSWORD, DEFAULT_ADMIN_PASSWORD
docker compose up --build
```

This starts Postgres, Kafka, Redis, a dev-mode Vault (seeded automatically),
the app (`web` + `worker` roles), Prometheus, Grafana, Loki/Promtail, Tempo,
`oms-bff`, `oms-gateway`, `product-service`, `customer-service`, and
`shipment-service` — the whole stack in one shot, given sibling checkouts of
all five (see [Sibling services](#sibling-services-separate-repos) above for
the expected directory layout).

- API: http://localhost:8080
- Swagger UI: http://localhost:8080/docs
- Actuator (health/info/prometheus): http://localhost:8081/actuator
- Grafana: http://localhost:3000 (`admin` / value of `GRAFANA_ADMIN_PASSWORD`)
- Prometheus: http://localhost:9090
- Tempo (via Grafana's Explore view, not a standalone UI)

### Running from an IDE

Set `DB_HOST`/`REDIS_HOST`/`KAFKA_BOOTSTRAP_SERVERS` to `localhost` (the
defaults already assume this) and run the infra-only services:

```bash
docker compose up postgres kafka redis
```

Vault is optional for local/IDE work — see [`vault/README.md`](vault/README.md)
if you want to exercise that path too.

### Default admin account

A user is seeded on first startup if none exist yet, using
`DEFAULT_ADMIN_USERNAME` / `DEFAULT_ADMIN_PASSWORD`. Change the password
after first login.

## API

Every domain exposes a standard REST CRUD surface plus search/filter
endpoints. Explore it via:

- **Swagger UI** at `/docs` (disabled in `prod`)
- **Bruno collections** in [`bruno/`](bruno) — import the folder into
  [Bruno](https://www.usebruno.com/) for ready-made requests against every
  endpoint, including auth, advanced search, status-update flows, and the
  `graphql/` folder for oms-bff's GraphQL surface.

## Configuration

Environment profiles: `dev` (default), `test`, `uat`, `prod` — see
`src/main/resources/application-{profile}.properties`. Required variables
per environment are documented in [`env.example`](env.example); `uat`/`prod`
fail fast at startup if a required secret or config value isn't supplied
(no silent insecure defaults).

A few configuration groups worth knowing about beyond the basics:

- **Service clients** — `app.productclient.*` / `app.customerclient.*`
  (base URL, timeouts) and the matching `resilience4j.circuitbreaker.instances.*`
  / `resilience4j.retry.instances.*` blocks. Base URLs default to
  `localhost:8082`/`localhost:8083` for local dev.
- **Tracing** — `management.otlp.tracing.endpoint` (defaults to a local
  Tempo/OTLP collector) and `management.tracing.sampling.probability`.
- **Cutover freeze flags** — `app.product.writes-frozen` /
  `app.customer.writes-frozen` / `app.shipment.writes-frozen`, all
  `false` outside an active maintenance window — see the stage3 runbooks
  in `docs/`.

## Testing

```bash
./mvnw test
```

Integration tests use Testcontainers (Postgres, Kafka) and don't require any
profile or external services beyond Docker. WireMock is available for
contract-testing the outbound `ProductClient`/`CustomerClient` calls without
a real product-service/customer-service instance running.

## Deployment

- **Docker Compose** — see above; also runs the full monitoring stack.
- **Kubernetes** — manifests in [`k8s/`](k8s), managed via Kustomize. Covers
  web/worker Deployments, HPA (web) and KEDA `ScaledObject` (worker),
  PodDisruptionBudget, Ingress, and Grafana/Prometheus/Loki wiring. See
  [`k8s/README.md`](k8s/README.md) for cluster prerequisites and the
  one-time Vault setup in [`vault/README.md`](vault/README.md).

## Monitoring

See [`monitoring/README.md`](monitoring/README.md) for the Prometheus/Grafana/
Loki/Tempo setup and the pre-built OMS Overview dashboard.

## Project structure

```
src/main/java/com/giri/oms/
├── auth/              # authentication, JWT issuing/verification
├── customer/          # still owned here — see extraction status above
├── customerclient/    # resilient client for customer-service, not yet wired into OrderServiceImpl
├── product/           # still owned here — see extraction status above
├── productclient/     # resilient client for product-service, not yet wired into OrderServiceImpl/InventoryServiceImpl
├── inventory/
├── order/
├── payment/
├── shipment/          # still owned here — see extraction status above
├── messaging/         # transactional outbox + Kafka event contracts (see docs/event-schema-versioning.md)
├── common/            # correlation IDs, rate limiting, locking, shared exceptions
└── security/          # JWKS endpoint
```

Each domain module follows the same layout: `controller/`, `service/`
(+ `service/impl/`), `repository/`, `entity/`, `dto/`, `mapper/`,
`specification/` (for dynamic search filters), and `exception/`. Each
module's `dto`, `service`, and `exception` sub-packages are marked
`@NamedInterface` (see `ModularityTests`) — that's the module's public
surface; `entity`/`repository`/`service.impl` are enforced-private to
everything else in this repo.

## Docs index

- [`docs/process-roles.md`](docs/process-roles.md) — the web/worker split.
- [`docs/event-schema-versioning.md`](docs/event-schema-versioning.md) —
  Kafka event compatibility policy.
- [`docs/stage3-data-cutover-runbook-product.md`](docs/stage3-data-cutover-runbook-product.md),
  [`-customer.md`](docs/stage3-data-cutover-runbook-customer.md),
  [`-shipment.md`](docs/stage3-data-cutover-runbook-shipment.md) — the
  maintenance-window procedures referenced above.

## License

_Add a license for this project (e.g. MIT, Apache-2.0) — none is currently
specified._