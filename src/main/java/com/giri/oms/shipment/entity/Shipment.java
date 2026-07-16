package com.giri.oms.shipment.entity;

import com.giri.oms.common.entity.BaseEntity;
import com.giri.oms.order.entity.Order;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "shipments")
public class Shipment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // An order can have more than one shipment row over its lifetime — e.g. a
    // returned shipment followed by a reship — so this is deliberately ManyToOne
    // rather than a OneToOne.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ShippingCarrier carrier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ShipmentStatus status = ShipmentStatus.PENDING;

    // Carrier-assigned tracking number. Typically absent at creation time and
    // populated once the shipment actually goes out (transition to SHIPPED).
    @Column(name = "tracking_number", length = 100)
    private String trackingNumber;

    // Timestamp of the PENDING -> SHIPPED transition. Left null until then.
    @Column(name = "shipped_at")
    private LocalDateTime shippedAt;

    // Timestamp of the -> DELIVERED transition. Left null until then.
    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;
}
