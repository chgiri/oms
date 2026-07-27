package com.giri.oms.inventory.entity;

import com.giri.oms.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "inventory", schema = "oms_inventory",
        uniqueConstraints = @UniqueConstraint(name = "uk_inventory_product_location", columnNames = {"product_id", "location"}))
public class Inventory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Plain FK column, not a @ManyToOne to the product module's Product entity —
    // see ModuleBoundaryTest. Unlike Order/OrderItem, inventory isn't a
    // historical record, so there's no name snapshot here: the product's
    // current name is resolved live via ProductService when building a
    // response (see InventoryServiceImpl).
    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false, length = 100)
    private String location;

    @Column(name = "quantity_available", nullable = false)
    private int quantityAvailable;

    @Column(name = "quantity_reserved", nullable = false)
    private int quantityReserved;

    @Column(name = "reorder_level", nullable = false)
    private int reorderLevel;
}
