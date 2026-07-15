package com.giri.oms.customer.specification;

import com.giri.oms.customer.entity.Customer;
import com.giri.oms.customer.entity.CustomerStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Each method returns a Specification<Customer> — essentially a lambda that builds
 * one WHERE condition. They're combined with .and()/.or() at the call site, so
 * filters compose instead of being hardcoded into one big query string.
 */
public class CustomerSpecification {

    private CustomerSpecification() {
        // utility class — no instances
    }

    public static Specification<Customer> hasName(String name) {
        return (root, query, cb) -> {
            if (name == null) return null;
            String pattern = "%" + name.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("firstName")), pattern),
                    cb.like(cb.lower(root.get("lastName")), pattern)
            );
        };
    }

    public static Specification<Customer> hasEmail(String email) {
        return (root, query, cb) ->
                email == null ? null : cb.like(cb.lower(root.get("email")), "%" + email.toLowerCase() + "%");
    }

    public static Specification<Customer> hasStatus(CustomerStatus status) {
        return (root, query, cb) ->
                status == null ? null : cb.equal(root.get("status"), status);
    }

    /**
     * Combines all filters, skipping any that are null (Specification.and() treats
     * a null Specification as a no-op condition automatically).
     */
    public static Specification<Customer> buildSearchSpec(String name, String email, CustomerStatus status) {
        return Specification.where(hasName(name))
                .and(hasEmail(email))
                .and(hasStatus(status));
    }

    /**
     * Alternative style: build the predicate list manually inside one Specification.
     * Useful when conditions need to reference each other or when you want a single
     * entry point instead of several small static methods.
     */
    public static Specification<Customer> searchCustomers(String name, String email, CustomerStatus status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (name != null && !name.isBlank()) {
                String pattern = "%" + name.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("firstName")), pattern),
                        cb.like(cb.lower(root.get("lastName")), pattern)
                ));
            }
            if (email != null && !email.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("email")), "%" + email.toLowerCase() + "%"));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}