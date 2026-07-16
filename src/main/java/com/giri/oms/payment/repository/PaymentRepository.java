package com.giri.oms.payment.repository;

import com.giri.oms.payment.entity.Payment;
import com.giri.oms.payment.entity.PaymentMethod;
import com.giri.oms.payment.entity.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long>, JpaSpecificationExecutor<Payment> {

    // Derived query methods — Spring parses the method name into SQL.
    // "OrderId" navigates the order relation's id field automatically.

    List<Payment> findByOrderId(Long orderId);

    List<Payment> findByStatus(PaymentStatus status);

    // JPQL @Query — for combining multiple optional filters in one query.
    @Query("""
            SELECT p FROM Payment p
            WHERE (:orderId IS NULL OR p.order.id = :orderId)
              AND (:status IS NULL OR p.status = :status)
              AND (:method IS NULL OR p.method = :method)
              AND (:minAmount IS NULL OR p.amount >= :minAmount)
              AND (:maxAmount IS NULL OR p.amount <= :maxAmount)
            """)
    Page<Payment> searchPayments(
            @Param("orderId") Long orderId,
            @Param("status") PaymentStatus status,
            @Param("method") PaymentMethod method,
            @Param("minAmount") BigDecimal minAmount,
            @Param("maxAmount") BigDecimal maxAmount,
            Pageable pageable
    );

}
