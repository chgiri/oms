package com.giri.oms.shipment.specification;

import com.giri.oms.shipment.entity.Shipment;
import com.giri.oms.shipment.entity.ShipmentStatus;
import com.giri.oms.shipment.entity.ShippingCarrier;
import org.springframework.data.jpa.domain.Specification;

/**
 * Each method returns a Specification<Shipment> — essentially a lambda that builds
 * one WHERE condition. They're combined with .and()/.or() at the call site, so
 * filters compose instead of being hardcoded into one big query string.
 */
public class ShipmentSpecification {

    private ShipmentSpecification() {
        // utility class — no instances
    }

    public static Specification<Shipment> hasOrderId(Long orderId) {
        return (root, query, cb) ->
                orderId == null ? null : cb.equal(root.get("order").get("id"), orderId);
    }

    public static Specification<Shipment> hasStatus(ShipmentStatus status) {
        return (root, query, cb) ->
                status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<Shipment> hasCarrier(ShippingCarrier carrier) {
        return (root, query, cb) ->
                carrier == null ? null : cb.equal(root.get("carrier"), carrier);
    }

    /**
     * Combines all filters, skipping any that are null (Specification.and() treats
     * a null Specification as a no-op condition automatically).
     */
    public static Specification<Shipment> buildSearchSpec(Long orderId, ShipmentStatus status, ShippingCarrier carrier) {
        return Specification.where(hasOrderId(orderId))
                .and(hasStatus(status))
                .and(hasCarrier(carrier));
    }
}
