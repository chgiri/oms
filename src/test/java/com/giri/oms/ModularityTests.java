package com.giri.oms;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Enforces module boundaries while oms-main is still a single deployable.
 *
 * ApplicationModules.of(OmsApplication.class) treats every direct sub-package
 * of com.giri.oms (auth, customer, inventory, order, payment, product,
 * shipment, security, common, messaging) as its own application module. By
 * default, only types sitting directly in a module's root package are that
 * module's public API — everything in a sub-package (entity, repository,
 * service.impl, mapper, specification, controller, ...) is internal and
 * off-limits to every other module.
 *
 * Since none of our modules actually put anything in their root package
 * (customer, order, etc. are all empty — real types live in customer.dto,
 * customer.service, and so on), each module instead opens exactly the
 * sub-packages that ARE meant to be its public surface via a package-info.java
 * annotated with @NamedInterface: `dto`, `service` (the interface, never
 * `service.impl`), and `exception` (thrown across the boundary and consumed
 * centrally by common.exception.GlobalExceptionHandler). `entity` and
 * `repository` are never opened — reading or writing another module's data is
 * only ever allowed through its public service interface.
 *
 * `common` and `messaging` are shared kernel / the sanctioned cross-module
 * event channel respectively, not business modules with private storage to
 * protect, so they're marked fully OPEN in their own package-info.java
 * instead of maintaining a NamedInterface allowlist for them.
 *
 * If verify() starts failing, it's telling you a genuine coupling was just
 * introduced — DON'T "fix" it by opening entity/repository or marking a
 * module OPEN. Fix the dependency: add or extend a method on the target
 * module's public service interface and call that instead.
 *
 * Inventory→Product, Payment→Order, and Shipment→Order all used to reach
 * directly into product.entity/product.repository and order.entity/
 * order.repository respectively. All three now go through ProductService /
 * OrderService only, and the owning entity (Inventory, Payment, Shipment)
 * stores the foreign id as a plain Long column instead of a JPA @ManyToOne —
 * see Order.customerId for the original example of that pattern. Payment's
 * one wrinkle: it needed to check the order's status, but OrderStatus itself
 * lives in order.entity (never exposed), so OrderService exposes a narrow
 * assertAwaitingPayment(Long) instead of leaking the enum.
 */
class ModularityTests {

    private static final ApplicationModules modules = ApplicationModules.of(OmsApplication.class);

    @Test
    void verifiesModularStructure() {
        modules.verify();
    }

    /**
     * Not a boundary check — just documents the current module structure.
     * Regenerates PlantUML diagrams under target/spring-modulith-docs on each
     * run; wire them into a docs site or PR check if you want drift visible
     * over time, but that's optional and this test never fails on its own.
     */
    @Test
    void writeDocumentationSnapshot() {
        new Documenter(modules)
                .writeModulesAsPlantUml()
                .writeIndividualModulesAsPlantUml();
    }
}