package com.giri.oms.payment.entity;

import com.giri.oms.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "payments", schema = "oms_payment")
public class Payment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Plain FK column, not a @ManyToOne to the order module's Order entity —
    // see ModularityTests. Existence and status are validated once, up front,
    // via OrderService (see PaymentServiceImpl.createPayment).
    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.PENDING;

    // Gateway/processor reference (e.g. a charge or transaction ID). Typically absent
    // at creation time and populated once the processor confirms the payment.
    @Column(name = "transaction_reference", length = 100)
    private String transactionReference;
}