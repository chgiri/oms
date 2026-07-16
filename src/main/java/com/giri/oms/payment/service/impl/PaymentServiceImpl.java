package com.giri.oms.payment.service.impl;

import com.giri.oms.common.dto.PagedResponse;
import com.giri.oms.common.exception.InvalidSortFieldException;
import com.giri.oms.order.entity.Order;
import com.giri.oms.order.exception.OrderNotFoundException;
import com.giri.oms.order.repository.OrderRepository;
import com.giri.oms.payment.constants.PaymentConstants;
import com.giri.oms.payment.dto.PaymentRequest;
import com.giri.oms.payment.dto.PaymentResponse;
import com.giri.oms.payment.entity.Payment;
import com.giri.oms.payment.entity.PaymentMethod;
import com.giri.oms.payment.entity.PaymentStatus;
import com.giri.oms.payment.exception.IllegalPaymentStateException;
import com.giri.oms.payment.exception.PaymentNotFoundException;
import com.giri.oms.payment.mapper.PaymentMapper;
import com.giri.oms.payment.repository.PaymentRepository;
import com.giri.oms.payment.service.PaymentService;
import com.giri.oms.payment.specification.PaymentSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // class-level default: every method is read-only unless overridden below
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final PaymentMapper paymentMapper;

    private static final Set<String> ALLOWED_SORT_FIELDS =
            Set.of("id", "amount", "status", "method", "createdAt", "updatedAt");

    // Defines which statuses a payment may move to from its current one. Any pair not
    // listed here (including staying in place) is rejected as an illegal transition.
    // FAILED and REFUNDED are terminal — absent as keys, so any move from them fails.
    private static final Map<PaymentStatus, Set<PaymentStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(PaymentStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(PaymentStatus.PENDING, EnumSet.of(PaymentStatus.COMPLETED, PaymentStatus.FAILED));
        ALLOWED_TRANSITIONS.put(PaymentStatus.COMPLETED, EnumSet.of(PaymentStatus.REFUNDED));
    }

    // Payments can only be deleted before money has actually moved (PENDING) or once
    // an attempt has definitively failed — once a payment has COMPLETED (or been
    // REFUNDED), deleting the record would silently lose the audit trail.
    private static final Set<PaymentStatus> DELETABLE_STATUSES = EnumSet.of(PaymentStatus.PENDING, PaymentStatus.FAILED);

    @Override
    @Transactional // write operation — overrides the class-level readOnly default
    public PaymentResponse createPayment(PaymentRequest request) {
        log.debug("Creating payment for order id: {}", request.getOrderId());

        Order order = getExistingOrder(request.getOrderId());

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setAmount(request.getAmount());
        payment.setMethod(request.getMethod());
        payment.setStatus(PaymentStatus.PENDING);

        Payment savedPayment = paymentRepository.save(payment);

        log.info(PaymentConstants.PAYMENT_CREATED_LOG, savedPayment.getId());
        return paymentMapper.mapToPaymentResponse(savedPayment);
    }

    @Override
    public PaymentResponse getPaymentById(Long paymentId) {
        log.debug("Fetching payment with id: {}", paymentId);
        return paymentMapper.mapToPaymentResponse(getExistingPayment(paymentId));
    }

    @Override
    public PagedResponse<PaymentResponse> getAllPayments(int pageNo, int pageSize, String sortBy, String sortDir) {
        log.debug("Fetching all payments");

        validateSortField(sortBy);

        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.DESC.name())
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(pageNo, pageSize, sort);

        Page<Payment> paymentPage = paymentRepository.findAll(pageable);
        Page<PaymentResponse> responsePage = paymentPage.map(paymentMapper::mapToPaymentResponse);

        return PagedResponse.of(responsePage);
    }

    private void validateSortField(String sortBy) {
        if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
            throw new InvalidSortFieldException(sortBy, ALLOWED_SORT_FIELDS);
        }
    }

    /**
     * Search endpoints take a raw Pageable straight from request query params (unlike
     * getAllPayments, which validates sortBy up front). A client can send any sort
     * property in any case, which — left unchecked — reaches Hibernate as a literal
     * JPQL path and blows up as an UnknownPathException (JPQL attribute paths are
     * case-sensitive). This validates each sort property against the same allow-list
     * and rewrites it to the correct case, so a case-insensitive match still works
     * and anything not on the allow-list gets a clean 400 via InvalidSortFieldException
     * instead of a 500.
     */
    private Pageable normalizeSort(Pageable pageable) {
        if (pageable.getSort().isUnsorted()) {
            return pageable;
        }

        List<Sort.Order> normalizedOrders = pageable.getSort().stream()
                .map(order -> {
                    String canonicalField = ALLOWED_SORT_FIELDS.stream()
                            .filter(field -> field.equalsIgnoreCase(order.getProperty()))
                            .findFirst()
                            .orElseThrow(() -> new InvalidSortFieldException(order.getProperty(), ALLOWED_SORT_FIELDS));
                    return new Sort.Order(order.getDirection(), canonicalField);
                })
                .toList();

        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(normalizedOrders));
    }

    @Override
    @Transactional // write operation — overrides the class-level readOnly default
    public PaymentResponse updatePaymentStatus(Long paymentId, PaymentStatus newStatus, String transactionReference) {
        log.debug("Updating payment id: {} status to: {}", paymentId, newStatus);

        Payment payment = getExistingPayment(paymentId);
        PaymentStatus currentStatus = payment.getStatus();

        Set<PaymentStatus> allowedNextStatuses = ALLOWED_TRANSITIONS.getOrDefault(currentStatus, Set.of());
        if (!allowedNextStatuses.contains(newStatus)) {
            log.warn("Rejected illegal status transition for payment id: {} — {} -> {}", paymentId, currentStatus, newStatus);
            throw new IllegalPaymentStateException(
                    String.format(PaymentConstants.INVALID_STATUS_TRANSITION_MESSAGE, paymentId, currentStatus, newStatus));
        }

        payment.setStatus(newStatus);
        if (transactionReference != null) {
            payment.setTransactionReference(transactionReference);
        }
        Payment updatedPayment = paymentRepository.save(payment);

        log.info(PaymentConstants.PAYMENT_STATUS_UPDATED_LOG, updatedPayment.getId(), newStatus);
        return paymentMapper.mapToPaymentResponse(updatedPayment);
    }

    @Override
    @Transactional // write operation — overrides the class-level readOnly default
    public void deletePayment(Long paymentId) {
        log.debug("Deleting payment with id: {}", paymentId);

        Payment payment = getExistingPayment(paymentId);
        if (!DELETABLE_STATUSES.contains(payment.getStatus())) {
            log.warn("Rejected delete of payment id: {} in non-deletable status: {}", paymentId, payment.getStatus());
            throw new IllegalPaymentStateException(
                    String.format(PaymentConstants.PAYMENT_NOT_DELETABLE_MESSAGE, paymentId, payment.getStatus()));
        }

        paymentRepository.deleteById(paymentId);

        log.info(PaymentConstants.PAYMENT_DELETED_LOG, paymentId);
    }

    @Override
    public Page<PaymentResponse> searchPayments(Long orderId, PaymentStatus status, PaymentMethod method,
                                                 BigDecimal minAmount, BigDecimal maxAmount, Pageable pageable) {
        Page<Payment> results = paymentRepository.searchPayments(orderId, status, method, minAmount, maxAmount, normalizeSort(pageable));
        return results.map(paymentMapper::mapToPaymentResponse);
    }

    @Override
    public Page<PaymentResponse> searchPaymentsBySpecification(Long orderId, PaymentStatus status, PaymentMethod method,
                                                                 BigDecimal minAmount, BigDecimal maxAmount, Pageable pageable) {
        var spec = PaymentSpecification.buildSearchSpec(orderId, status, method, minAmount, maxAmount);
        Page<Payment> results = paymentRepository.findAll(spec, normalizeSort(pageable));
        return results.map(paymentMapper::mapToPaymentResponse);
    }

    private Payment getExistingPayment(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> {
                    log.warn("Payment not found with id: {}", paymentId);
                    return new PaymentNotFoundException(paymentId);
                });
    }

    private Order getExistingOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> {
                    log.warn("Order not found with id: {} while recording payment", orderId);
                    return new OrderNotFoundException(orderId);
                });
    }

}
