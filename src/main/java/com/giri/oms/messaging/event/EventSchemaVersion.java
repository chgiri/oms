package com.giri.oms.messaging.event;

/**
 * Compatibility contract for every record in this package.
 *
 * <p>These events are published on shared Kafka topics ({@code order-events},
 * {@code product-events}) and consumed by other modules today, other
 * deployable services once the microservices-prep plan splits them out (see
 * ProductEventInventoryConsumer/OrderSagaEventConsumer javadoc). Once
 * producer and consumer stop deploying together, the wire format of these
 * records is a real compatibility contract, not just an internal DTO.
 *
 * <h2>Policy</h2>
 * <ul>
 *   <li><b>Additive-only.</b> New fields must be optional in effect: give
 *   consumers on the previous {@code schemaVersion} something safe to do
 *   when the field is simply absent (Jackson leaves a missing record
 *   component at its Java default — {@code null} for objects, {@code 0}/
 *   {@code false} for primitives — as long as
 *   {@code spring.jackson.deserialization.fail-on-unknown-properties=false},
 *   see application.properties). Never assume every consumer already knows
 *   about a field the moment a producer starts sending it.</li>
 *   <li><b>Never remove, rename, or repurpose a field.</b> An old consumer
 *   still reading it must keep getting the same meaning it always had. If a
 *   field is genuinely obsolete, stop relying on it but leave it in place
 *   (or deprecate in the javadoc) rather than deleting it.</li>
 *   <li><b>Never narrow or change a field's type</b> (e.g. String -&gt; enum,
 *   widening BigDecimal precision assumptions) without a version bump —
 *   that can silently break a consumer's deserialization instead of just
 *   ignoring an unknown property.</li>
 *   <li><b>{@code schemaVersion} only bumps for a breaking change</b> to that
 *   specific event's shape — not for every additive field. When it does bump,
 *   the event should keep publishing under the same {@code eventType} header
 *   value, and consumers that need to tell the shapes apart branch on
 *   {@code schemaVersion} (a new {@code EventType} constant, e.g.
 *   {@code OrderCreatedV2}, is the alternative worth reaching for if the
 *   change is big enough that a single consumer method branching on version
 *   gets unreadable).</li>
 * </ul>
 *
 * <h2>Why a version field instead of a schema registry</h2>
 * Avro/Protobuf plus a registry (Confluent Schema Registry, Apicurio, ...)
 * enforces this policy at publish time instead of relying on code review and
 * discipline — worth adopting once producers and consumers of these events
 * actually live in separately deployed services. While everything here is
 * one Spring Boot monolith deploying atomically, that enforcement has no
 * consumer to protect yet; a documented policy plus this version field is
 * the right amount of ceremony for this stage, and the field itself is what
 * makes a later migration to a registry-backed format traceable (every event
 * ever published already says which shape it is).
 */
public final class EventSchemaVersion {

    /**
     * The current version of every event in this package. All ten records
     * are still on their original shape, so there's just the one constant —
     * once any single event's shape needs a breaking change, give that
     * event its own version constant here (e.g. {@code ORDER_CREATED_V2 = 2})
     * rather than bumping this shared one, since bumping a shared constant
     * would incorrectly imply every other event changed too.
     */
    public static final int V1 = 1;

    private EventSchemaVersion() {
    }
}
