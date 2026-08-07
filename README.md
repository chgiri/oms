# OMS — Order Management System

A domain-driven order management backend built with Spring Boot: customers,
products, inventory, orders, payments, and shipments, connected by an
event-driven saga instead of direct service-to-service calls.

**This repository is a monolith in the middle of being decomposed into
microservices** — see [Microservices extraction status](#microservices-extraction-status)
below before assuming any given module is still owned here.

## Features

- **Domain modules** — `auth`, `inventory`, `order`, `payment`, each with
  its own controller/service/repository/DTO layers, plus
  `productclient`/`customerclient` (resilient HTTP clients — `productclient`
  calls out to product-service, `customerclient` to customer-service; see
  below). `product` and `customer` used to be here too — as of Stage 5 of
  the microservices-prep plan both have been fully removed, the same way
  `shipment` was: `OrderServiceImpl`/`InventoryServiceImpl` reach
  product-service/customer-service exclusively through `productclient`/
  `customerclient` now, with no in-process module left to reach into for
  either — see
  [Microservices extraction status](#microservices-extraction-status) below.
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
out into their own deployables. **All three have had their data cutover
executed (Stage 3) and confirmed stable, and all three have now gone all the
way through Stage 5 — oms-main no longer contains any of the three modules
at all.** This table is a historical record of that progression at this
point, not a live status check — if you're reading this later and
considering starting a fourth extraction, the pattern below is the one to
follow, not a sign there's still cutover work pending on these three.

| Module | Sibling service repo | Scaffold + API contract (Stages 1-2) | Data cutover runbook (Stage 3) | Call sites swapped in oms-main (Stage 4) | Removed from oms-main (Stage 5) |
|---|---|---|---|---|---|
| Product | `product-service` | Done | **Executed** — [runbook](docs/stage3-data-cutover-runbook-product.md) | **Done** | **Done** |
| Customer | `customer-service` | Done | **Executed** — [runbook](docs/stage3-data-cutover-runbook-customer.md) | **Done** | **Done** |
| Shipment | `shipment-service` | Done | **Executed** — [runbook](docs/stage3-data-cutover-runbook-shipment.md) | N/A — see note | **Done** |

Practically, this means:

- `ProductClient`/`CustomerClient` (in `productclient`/`customerclient`) are
  the only paths left to either service — `OrderServiceImpl`/
  `InventoryServiceImpl` call product-service/customer-service for every
  product/customer lookup, real HTTP calls, not an in-process module.
  Neither module's own controller/service/entity/repository exists in
  oms-main anymore for either Product or Customer.
- The `stage3-*` runbooks are the maintenance-window procedures that were
  followed for copying each module's data into its own service's database
  and (for Product/Customer) cutting `OrderServiceImpl` over to the client.
  Shipment had no equivalent Stage 4 deploy — nothing in oms-main called
  `ShipmentService` synchronously, so there was no in-process call site to
  swap; shipment-service's own `OrderClient` (calling back out to oms-main)
  covered that from Stage 2 onward.
- All three `stage3-*` runbooks (linked above) are marked **EXECUTED** at
  the top of each document — they're historical records of the
  maintenance-window procedure that was followed for each module, not
  pending tasks. Same for their matching `scripts/stage3-copy-*.sh` — each
  one now carries an "already run" banner too.
- Shipment had a wrinkle Product/Customer didn't: `OrderConfirmedShipmentConsumer`
  auto-created shipments off a Kafka event, not just REST — its cutover
  runbook has an extra step (stopping oms-main's consumer group membership
  entirely before shipment-service's own copy started) that the Product/
  Customer runbooks didn't need.
- Product and Customer both had a wrinkle Shipment never had:
  `product.exception.ProductNotFoundException`/`customer.exception.CustomerNotFoundException`
  couldn't just be deleted at Stage 5 — `ProductClient`'s/`CustomerClient`'s
  own not-found contracts depend on them. Both were relocated instead — to
  `productclient.exception.ProductNotFoundException` and
  `customerclient.exception.CustomerNotFoundException` respectively, not
  removed. Same `ErrorCode.PRODUCT_NOT_FOUND`/`CUSTOMER_NOT_FOUND`
  (`EPR100`/`ECU100`, unchanged) either way — this only moved which Java
  package owns the class, not the wire-level contract a caller sees.
- Customer had one wrinkle Product didn't:
  `customer.exception.CustomerEmailAlreadyExistsException` had no
  equivalent on the Product side at all — that was `CustomerServiceImpl`'s
  own in-process uniqueness validation, with nothing in `CustomerClient`'s
  read-only `getCustomer(id)` that could ever throw it. It retired outright
  rather than relocating anywhere — see `ErrorCode.CUSTOMER_EMAIL_ALREADY_EXISTS`'s
  own `RETIRED` note.
- `app.product.writes-frozen`/`app.customer.writes-frozen` (both modules'
  maintenance-window freeze flags) and their exception classes
  (`ProductWritesFrozenException`/`CustomerWritesFrozenException`) no
  longer exist in this codebase — removed along with their respective
  packages at Stage 5, same as Shipment's equivalent (`app.shipment.writes-frozen`)
  was.
- `ErrorCode`'s `PR501`/`CU501`/`CU101` (`PRODUCT_WRITES_FROZEN`/
  `CUSTOMER_WRITES_FROZEN`/`CUSTOMER_EMAIL_ALREADY_EXISTS`) and
  `SH100`/`SH101`/`SH501` (`SHIPMENT_NOT_FOUND`/`ILLEGAL_SHIPMENT_STATE`/
  `SHIPMENT_WRITES_FROZEN`) entries are all still present in
  `ErrorCode.java`, marked retired in a comment there — kept per that
  class's own append-only policy (never reassign or remove a published
  code) even though nothing in this repo throws them anymore. Unlike
  Shipment's three, though, Product's and Customer's other two codes each
  (`PR100`/`PRODUCT_NOT_FOUND`, `PR500`/`PRODUCT_SERVICE_UNAVAILABLE`,
  `CU100`/`CUSTOMER_NOT_FOUND`, `CU500`/`CUSTOMER_SERVICE_UNAVAILABLE`) are
  NOT retired — still very much alive, just owned by `productclient`/
  `customerclient` now instead of the deleted `product`/`customer`
  packages. See `ErrorCode.java`'s own comments on those blocks for the
  full explanation of that split.
- `ModularityTests` no longer treats `product`, `customer`, or `shipment`
  as modules it enforces boundaries for at all — none of the three
  packages exists in this repo anymore. `productclient` and `customerclient`
  are the modules it verifies today for what used to be their coupling.
- The `oms_product`/`oms_customer`/`oms_shipment` Postgres schemas were
  dropped by `V23__drop_oms_product_schema.sql`,
  `V24__drop_oms_customer_schema.sql`, and `V22__drop_oms_shipment_schema.sql`
  respectively — `V1__init_schema.sql`'s original `CREATE TABLE`s stay in
  the migration history untouched, same as every other historical
  migration in this project; only the live schemas were removed.
- A few tests that used to create real `Customer`/`Product` rows purely as
  fixtures (no actual FK dependency — those were dropped back in Phase 2's
  `V19__drop_cross_module_fk_constraints.sql`) needed updating once those
  entities stopped existing: `OrderRepositoryTest`, `PaymentRepositoryTest`,
  `InventoryRepositoryTest`, `OrderOptimisticLockingTest`, and
  `OrderCreatedOutboxIntegrationTest` all switched from real saved rows to
  plain `Long` id constants. `SecurityIntegrationTest`'s admin-only-delete
  authorization test had a deeper wrinkle — it specifically exercised
  `DELETE /api/v1/customers/{id}` because that endpoint required `ADMIN`;
  with `CustomerController` gone, it now exercises the equivalent
  `DELETE /api/v1/inventory/{id}` instead (same any-role-creates/
  admin-only-deletes shape), with `ProductClient` mocked so
  `createInventory`'s product-existence check doesn't need a real
  product-service running in that test's context.
- **Cross-repo follow-up, not fixable from this repo:** `bruno/graphql/`'s
  `OrderDetail`/`OrderDetailMinimal` queries (oms-bff's GraphQL schema, not
  oms-main's own API) still select a `shipment` field on an order. If
  oms-bff's resolver for that field was calling oms-main's now-deleted
  `/shipments` endpoints, it needs to be repointed at shipment-service —
  that's a change in the `oms-bff` repo, outside what this repo can address.

## Sibling services (separate repos)

Beyond the three extraction targets above, two other services sit around
oms-main and are **not** part of this repository:

- **`oms-bff`** — a GraphQL BFF in front of oms-main, for frontend
  consumption patterns REST doesn't fit well (see `bruno/graphql/`).
- **`oms-gateway`** — a Spring Cloud Gateway sitting at the edge: JWT
  validation, Redis-backed rate limiting, and CORS, in front of the whole
  system — not just oms-main. It path-routes to product-service,
  customer-service, and shipment-service directly (`/api/v1/products/**`,
  `/api/v1/customers/**`, `/api/v1/shipments/**`), falling through to
  oms-main for everything else still owned there.

Both are referenced from `docker-compose.yml` as sibling checkouts (expected
at `../oms-bff` and `../oms-gateway` relative to this repo) with their own
Dockerfiles and READMEs — this README doesn't attempt to document them
beyond that pointer.

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
`oms-bff`, `oms-gateway`, `product-service`, `customer-service`, and
`shipment-service` (see [Sibling services](#sibling-services-separate-repos)
above for the expected directory layout). Docker Compose's half of Stage 7
is done for all three extracted services now — see
[Microservices extraction status](#microservices-extraction-status) above.
The other half — each service's own `k8s/` manifests and CI pipeline, in
its own repo, not this one — is also done for `product-service` and
`customer-service` (see their respective `k8s/README.md`)
**Unlike Product/Customer, running `shipment-service` isn't optional if you
need shipment functionality**: Stage 5 already removed the `shipment`
module from oms-main entirely, so without it there's no shipment
functionality anywhere in your local stack at all — no fallback in oms-main
to fall back on.

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
- **Cutover freeze flags** — none remain in this codebase.
  `app.product.writes-frozen`, `app.customer.writes-frozen`, and
  `app.shipment.writes-frozen` all no longer exist — each was removed along
  with its respective package once that module reached Stage 5. See the
  stage3 runbooks in `docs/` for the maintenance-window procedure that used
  them while they were live.

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
Loki/Tempo setup. Grafana auto-provisions three dashboards, not just one —
the original OMS Overview plus a companion **product-service Overview** and
**customer-service Overview** (`monitoring/grafana/dashboards/*.json`), each
scoped to its own `application` label so their panels never mix on the
shared instance. No equivalent for shipment-service yet — unconfirmed
whether that's been built.

## Project structure

```
src/main/java/com/giri/oms/
├── auth/              # authentication, JWT issuing/verification
├── customerclient/    # resilient client for customer-service, wired into OrderServiceImpl as of Stage 4
├── productclient/     # resilient client for product-service, wired into OrderServiceImpl/InventoryServiceImpl as of Stage 4
├── inventory/
├── order/
├── payment/
├── messaging/         # transactional outbox + Kafka event contracts (see docs/event-schema-versioning.md)
├── common/            # correlation IDs, rate limiting, locking, shared exceptions
└── security/          # JWKS endpoint
```

There's no `product/`, `customer/`, or `shipment/` here anymore — as of
Stage 5 of the microservices-prep plan all three have been fully extracted,
into product-service, customer-service, and shipment-service respectively;
see [Microservices extraction status](#microservices-extraction-status)
above. `productclient`/`customerclient` stayed (they're the clients, not
the modules).

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