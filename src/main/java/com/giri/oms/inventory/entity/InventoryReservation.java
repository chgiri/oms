package com.giri.oms.inventory.entity;

import com.giri.oms.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Marks that inventory has already been reserved for a given (order, product) pair.
 * The unique constraint on (order_id, product_id) is what makes reservation
 * idempotent: inserting this row is the first thing the consumer does for a line
 * item, and a duplicate-delivered OrderCreated event will fail that insert instead
 * of decrementing stock a second time.
 *
 * Deliberately not a JPA relationship to Order/Product (just raw ids) — this table
 * is written from the inventory module's Kafka consumer, and Order lives in a
 * different module. Keeping it a plain id avoids pulling the Order entity/aggregate
 * across that boundary for what is really just an idempotency/audit record.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "inventory_reservations",
        uniqueConstraints = @UniqueConstraint(name = "uk_inventory_reservations_order_product", columnNames = {"order_id", "product_id"}))
public class InventoryReservation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    // The OrderCreated event's eventId — kept for traceability (which delivery of
    // the event produced this reservation), not for the idempotency check itself.
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(nullable = false)
    private int quantity;

    public static InventoryReservation of(Long orderId, Long productId, UUID eventId, int quantity) {
        InventoryReservation reservation = new InventoryReservation();
        reservation.orderId = orderId;
        reservation.productId = productId;
        reservation.eventId = eventId;
        reservation.quantity = quantity;
        return reservation;
    }
}
