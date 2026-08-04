package com.giri.oms;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Enforces module boundaries while oms-main is still a single deployable.
 *
 * ApplicationModules.of(OmsApplication.class) treats every direct sub-package
 * of com.giri.oms (auth, customer, inventory, order, payment, product,
 * productclient, security, common, messaging) as its own application module. By
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
 * Inventory→Product and Payment→Order both used to reach directly into
 * product.entity/product.repository and order.entity/order.repository
 * respectively. Both now go through ProductService/OrderService only, and
 * the owning entity (Inventory, Payment) stores the foreign id as a plain
 * Long column instead of a JPA @ManyToOne — see Order.customerId for the
 * original example of that pattern. Payment's one wrinkle: it needed to
 * check the order's status, but OrderStatus itself lives in order.entity
 * (never exposed), so OrderService exposes a narrow
 * assertAwaitingPayment(Long) instead of leaking the enum.
 *
 * Order→Product and Inventory→Product (the "both now go through
 * ProductService" sentence above) changed again as of Phase 4 Stage 4 of the
 * microservices-prep plan: OrderServiceImpl/InventoryServiceImpl no longer
 * depend on the product module's service interface at all — they go through
 * productclient.service.ProductClient instead, a real HTTP call to the
 * now-separately-deployed product-service. product is gone now as of Stage 5
 * — same pattern as Shipment below, and the same reasoning: a module already
 * only reachable through a public service interface (ProductClient by that
 * point, not even product's own ProductService) had nothing left to untangle
 * once it was time to delete it. One wrinkle Shipment didn't have: product's
 * ProductNotFoundException couldn't be deleted outright — ProductClient's own
 * not-found contract still throws it, so it was relocated to
 * productclient.exception.ProductNotFoundException rather than removed (see
 * that class's own Javadoc).
 *
 * Shipment used to be a fourth module here, with the exact same
 * Shipment→Order coupling described above (a plain orderId Long column,
 * validated via OrderService rather than a JPA relation). It's gone now —
 * fully extracted into shipment-service as of Stage 5 of the
 * microservices-prep plan — which is exactly what that pattern was for:
 * a module already only reaching its neighbor through a public service
 * interface, with no direct entity/repository coupling, has nothing left to
 * untangle when it's time to move it out into its own deployable.
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