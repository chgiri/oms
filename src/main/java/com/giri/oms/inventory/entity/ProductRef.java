package com.giri.oms.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Inventory's own local read-only copy of just the product fields it needs
 * for display (Phase 1 step 3 of the microservices-prep plan). This is the
 * "(b) local read-only replica" side of the inventory -> product FK decision
 * — see the plan doc: four of five cross-module FKs are write-time-only
 * (option (a), a synchronous ProductService call), but Inventory reads
 * product data on every single row of every listing/search, so a replica
 * kept in sync via Kafka avoids turning that into an N+1 network call once
 * Product is a separate service.
 *
 * Deliberately NOT a JPA association to product.entity.Product and doesn't
 * extend BaseEntity — this table has no relationship to the product module's
 * schema at all (see ModularityTests: entity/repository packages are never
 * exposed across modules, so this couldn't be a real FK/association even if
 * we wanted one). productId is assigned from the event, not generated, and
 * updatedAt is the event's own occurredAt time rather than a
 * @UpdateTimestamp on write — so this table's timestamp reflects when the
 * *product* last changed, not when this row last happened to be upserted
 * (those are the same in practice, but the intent is different, and Hibernate's
 * own clock isn't swappable in tests the way the injected Clock bean is — see
 * BaseEntity's KNOWN GAP note).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "product_ref")
public class ProductRef {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
