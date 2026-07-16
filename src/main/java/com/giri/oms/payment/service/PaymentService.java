package com.giri.oms.payment.service;

import com.giri.oms.common.dto.PagedResponse;
import com.giri.oms.payment.dto.PaymentRequest;
import com.giri.oms.payment.dto.PaymentResponse;
import com.giri.oms.payment.entity.PaymentMethod;
import com.giri.oms.payment.entity.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;

public interface PaymentService {

    PaymentResponse createPayment(PaymentRequest request);

    PaymentResponse getPaymentById(Long paymentId);

    PagedResponse<PaymentResponse> getAllPayments(int pageNo, int pageSize, String sortBy, String sortDir);

    PaymentResponse updatePaymentStatus(Long paymentId, PaymentStatus newStatus, String transactionReference);

    void deletePayment(Long paymentId);

    Page<PaymentResponse> searchPayments(Long orderId, PaymentStatus status, PaymentMethod method,
                                          BigDecimal minAmount, BigDecimal maxAmount, Pageable pageable);

    Page<PaymentResponse> searchPaymentsBySpecification(Long orderId, PaymentStatus status, PaymentMethod method,
                                                          BigDecimal minAmount, BigDecimal maxAmount, Pageable pageable);

}
