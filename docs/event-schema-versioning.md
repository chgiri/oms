# Kafka event schema versioning

## The problem this solves

Every event in `com.giri.oms.messaging.event` — `OrderCreatedEvent`,
`PaymentConfirmedEvent`, `ProductUpdatedEvent`, and the rest — is a plain
Jackson-serialized Java record with no explicit contract or version. That's
fine as long as the module that publishes an event and the module that
consumes it deploy together, in the same JAR, at the same time: if a field
changes shape, every reader of it changes in the same deploy.

That stops being true the moment any of these producers and consumers
deploy independently — which is the entire point of the microservices-prep
phases already called out in this codebase (see `ProductEventFactory`,
`ProductEventInventoryConsumer`, and the `EventType` javadoc). Once that
split happens, an old consumer can be running against a new producer's
events (or vice versa) for as long as a rollout takes, and nothing stops a
field being renamed, retyped, or removed out from under it.

## The policy

This is deliberately a documented convention plus a version field, not a
schema registry — see "Why not Avro/a schema registry yet" below for why
that's the right amount of ceremony for where this project is today.

1. **Additive-only.** New fields are the only kind of change that's safe
   without a version bump. A consumer still on the previous shape simply
   never sees the new field; a consumer that already knows about it gets
   the value. Each `@KafkaListener` consumer (`OrderSagaEventConsumer`,
   `ProductEventInventoryConsumer`, `OrderCreatedInventoryConsumer`,
   `OrderConfirmedShipmentConsumer`) reads its event payloads through a
   private `readEvent(...)` helper that overrides
   `DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES` off per read (via
   `JsonMapper.readerFor(...).without(...)`), rather than on the app's
   shared `JsonMapper` bean — that keeps REST request/response bodies (and
   everything else that injects the default `JsonMapper`) on Jackson's
   normal strict behavior, and confines the tolerant reading to exactly
   the four places that consume events.
2. **Never remove, rename, or repurpose a field.** If a field is genuinely
   obsolete, stop relying on it in new code but leave it on the record (or
   mark it deprecated in the javadoc) — deleting it breaks any consumer that
   hasn't redeployed past the point where it stopped needing it.
3. **Never narrow or change a field's type** (`String` → enum, a
   `BigDecimal` scale assumption, etc.) without a version bump. Unlike an
   added field, a type change doesn't fail closed — it can deserialize into
   something silently wrong instead of just being ignored.
4. **`schemaVersion` bumps only for a breaking change to that specific
   event**, not for every additive field — see `EventSchemaVersion`'s
   javadoc for the constant-naming convention when that happens. The event
   keeps its existing `eventType` header value; a consumer that needs to
   tell the two shapes apart branches on `schemaVersion`. If the change is
   big enough that branching in one consumer method gets unreadable, publish
   the new shape under a new `EventType` constant instead (e.g.
   `OrderCreatedV2`) rather than overloading one method with version
   branches.

Every event record now carries a `schemaVersion` field (currently
`EventSchemaVersion.V1` on all ten) so that policy has something concrete to
attach to, and so any future migration to a registry-backed format has a
version already stamped on every event ever published.

## Why not Avro/a schema registry yet

A schema registry (Confluent Schema Registry, Apicurio, ...) backed by
Avro or Protobuf enforces this same policy at publish time — a producer
literally cannot publish a breaking change without the registry rejecting
it — instead of relying on code review and this document. That's worth
adopting once the producers and consumers of these events actually live in
separately deployed services with independent release cadences, where a
human missing this doc in review has real blast radius.

While everything here is one Spring Boot monolith that deploys as a single
atomic unit, there's no independent consumer yet for a registry to protect
against a bad deploy — the modules are still compiled and shipped together.
Introducing Avro/Protobuf now would mean generated-code plumbing and a new
piece of infrastructure (the registry itself) for a guarantee the current
deployment model already gives for free. The right trigger to revisit this
is the same one called out in `ProductEventFactory`'s javadoc: whenever a
module here is actually split out into its own deployable service.
