package com.giri.oms.order.repository;

import com.giri.oms.order.entity.Order;
import com.giri.oms.order.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    // Derived query methods — Spring parses the method name into SQL.
    // "CustomerId" navigates the customer relation's id field automatically.

    List<Order> findByCustomerId(Long customerId);

    List<Order> findByStatus(OrderStatus status);

    // JPQL @Query — for combining multiple optional filters in one query.
    @Query("""
            SELECT o FROM Order o
            WHERE (:customerId IS NULL OR o.customer.id = :customerId)
              AND (:status IS NULL OR o.status = :status)
              AND (:minTotal IS NULL OR o.totalAmount >= :minTotal)
              AND (:maxTotal IS NULL OR o.totalAmount <= :maxTotal)
            """)
    Page<Order> searchOrders(
            @Param("customerId") Long customerId,
            @Param("status") OrderStatus status,
            @Param("minTotal") BigDecimal minTotal,
            @Param("maxTotal") BigDecimal maxTotal,
            Pageable pageable
    );

}
