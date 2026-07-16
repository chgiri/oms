package com.giri.oms.order.specification;

import com.giri.oms.order.entity.Order;
import com.giri.oms.order.entity.OrderStatus;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;

/**
 * Each method returns a Specification<Order> — essentially a lambda that builds
 * one WHERE condition. They're combined with .and()/.or() at the call site, so
 * filters compose instead of being hardcoded into one big query string.
 */
public class OrderSpecification {

    private OrderSpecification() {
        // utility class — no instances
    }

    public static Specification<Order> hasCustomerId(Long customerId) {
        return (root, query, cb) ->
                customerId == null ? null : cb.equal(root.get("customer").get("id"), customerId);
    }

    public static Specification<Order> hasStatus(OrderStatus status) {
        return (root, query, cb) ->
                status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<Order> hasMinTotal(BigDecimal minTotal) {
        return (root, query, cb) ->
                minTotal == null ? null : cb.greaterThanOrEqualTo(root.get("totalAmount"), minTotal);
    }

    public static Specification<Order> hasMaxTotal(BigDecimal maxTotal) {
        return (root, query, cb) ->
                maxTotal == null ? null : cb.lessThanOrEqualTo(root.get("totalAmount"), maxTotal);
    }

    /**
     * Combines all filters, skipping any that are null (Specification.and() treats
     * a null Specification as a no-op condition automatically).
     */
    public static Specification<Order> buildSearchSpec(Long customerId, OrderStatus status,
                                                        BigDecimal minTotal, BigDecimal maxTotal) {
        return Specification.where(hasCustomerId(customerId))
                .and(hasStatus(status))
                .and(hasMinTotal(minTotal))
                .and(hasMaxTotal(maxTotal));
    }
}
