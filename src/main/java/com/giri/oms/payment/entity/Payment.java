package com.giri.oms.payment.entity;

import com.giri.oms.common.entity.BaseEntity;
import com.giri.oms.order.entity.Order;
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
@Table(name = "payments")
public class Payment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // An order can have more than one payment row over its lifetime — e.g. a
    // failed attempt followed by a successful retry — so this is deliberately
    // ManyToOne rather than a OneToOne.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

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
