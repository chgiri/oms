package com.giri.oms.order.entity;

import com.giri.oms.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "order_items", schema = "oms_order")
public class OrderItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    // Plain FK column, not a @ManyToOne to the product module's Product entity —
    // see the same note on Order.customerId. Existence and price are resolved
    // once, up front, via ProductService.getProductById.
    @Column(name = "product_id", nullable = false)
    private Long productId;

    // Snapshot of the product's name at order-placement time, for the same
    // reason unitPrice below is a snapshot — a product rename later shouldn't
    // rewrite what a past order shows, and it avoids a ProductService round-trip
    // on every read of an order.
    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    @Column(nullable = false)
    private int quantity;

    // Snapshot of the product's price at the time the order was placed — deliberately
    // NOT re-read from Product later, so a subsequent price change never alters the
    // amount already recorded on a past order.
    @Column(name = "unit_price", nullable = false, precision = 7, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;
}
