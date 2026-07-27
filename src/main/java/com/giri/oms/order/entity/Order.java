package com.giri.oms.order.entity;

import com.giri.oms.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "orders", schema = "oms_order")
public class Order extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Deliberately a plain FK column, not a @ManyToOne to the customer module's
    // Customer entity — the order module doesn't reach into another module's
    // entity/repository packages (see ModuleBoundaryTest). Existence is validated
    // once, up front, via CustomerService.getCustomerById.
    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    // Snapshot of the customer's name at order-placement time — same reasoning as
    // OrderItem.unitPrice below: a customer renaming themselves later shouldn't
    // silently rewrite the name on a historical order, and it avoids a
    // CustomerService round-trip on every read of an order.
    @Column(name = "customer_name", nullable = false, length = 201)
    private String customerName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.PENDING;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    // cascade = ALL + orphanRemoval: items are owned entirely by their order — saving
    // the order saves its items, and removing an item from this list (not just
    // deleting the order) deletes that item's row too.
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<OrderItem> items = new ArrayList<>();

    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }
}
