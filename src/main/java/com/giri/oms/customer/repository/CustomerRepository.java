package com.giri.oms.customer.repository;

import com.giri.oms.customer.entity.Customer;
import com.giri.oms.customer.entity.CustomerStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long>, JpaSpecificationExecutor<Customer> {

    // Derived query methods — Spring parses the method name into SQL.

    Optional<Customer> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    List<Customer> findByStatus(CustomerStatus status);

    // JPQL @Query — for combining multiple optional filters in one query.

    @Query("""
            SELECT c FROM Customer c
            WHERE (:name IS NULL
                    OR LOWER(c.firstName) LIKE LOWER(CONCAT('%', CAST(:name AS string), '%'))
                    OR LOWER(c.lastName) LIKE LOWER(CONCAT('%', CAST(:name AS string), '%')))
              AND (:email IS NULL OR LOWER(c.email) LIKE LOWER(CONCAT('%', CAST(:email AS string), '%')))
              AND (:status IS NULL OR c.status = :status)
            """)
    Page<Customer> searchCustomers(
            @Param("name") String name,
            @Param("email") String email,
            @Param("status") CustomerStatus status,
            Pageable pageable
    );

    // Native query — Postgres-specific full-text search across name and email.
    // Use sparingly — ties you to one DB vendor.
    @Query(value = """
            SELECT * FROM customers c
            WHERE to_tsvector('english', c.first_name || ' ' || c.last_name || ' ' || c.email)
                @@ plainto_tsquery('english', :keyword)
            """, nativeQuery = true)
    List<Customer> fullTextSearch(@Param("keyword") String keyword);

}