package com.giri.oms.payment.specification;

import com.giri.oms.payment.entity.Payment;
import com.giri.oms.payment.entity.PaymentMethod;
import com.giri.oms.payment.entity.PaymentStatus;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;

/**
 * Each method returns a Specification<Payment> — essentially a lambda that builds
 * one WHERE condition. They're combined with .and()/.or() at the call site, so
 * filters compose instead of being hardcoded into one big query string.
 */
public class PaymentSpecification {

    private PaymentSpecification() {
        // utility class — no instances
    }

    public static Specification<Payment> hasOrderId(Long orderId) {
        return (root, query, cb) ->
                orderId == null ? null : cb.equal(root.get("order").get("id"), orderId);
    }

    public static Specification<Payment> hasStatus(PaymentStatus status) {
        return (root, query, cb) ->
                status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<Payment> hasMethod(PaymentMethod method) {
        return (root, query, cb) ->
                method == null ? null : cb.equal(root.get("method"), method);
    }

    public static Specification<Payment> hasMinAmount(BigDecimal minAmount) {
        return (root, query, cb) ->
                minAmount == null ? null : cb.greaterThanOrEqualTo(root.get("amount"), minAmount);
    }

    public static Specification<Payment> hasMaxAmount(BigDecimal maxAmount) {
        return (root, query, cb) ->
                maxAmount == null ? null : cb.lessThanOrEqualTo(root.get("amount"), maxAmount);
    }

    /**
     * Combines all filters, skipping any that are null (Specification.and() treats
     * a null Specification as a no-op condition automatically).
     */
    public static Specification<Payment> buildSearchSpec(Long orderId, PaymentStatus status, PaymentMethod method,
                                                           BigDecimal minAmount, BigDecimal maxAmount) {
        return Specification.where(hasOrderId(orderId))
                .and(hasStatus(status))
                .and(hasMethod(method))
                .and(hasMinAmount(minAmount))
                .and(hasMaxAmount(maxAmount));
    }
}
