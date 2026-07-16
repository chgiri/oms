package com.giri.oms.payment.service;

import com.giri.oms.common.dto.PagedResponse;
import com.giri.oms.common.exception.InvalidSortFieldException;
import com.giri.oms.customer.entity.Customer;
import com.giri.oms.customer.entity.CustomerStatus;
import com.giri.oms.order.entity.Order;
import com.giri.oms.order.entity.OrderStatus;
import com.giri.oms.order.exception.OrderNotFoundException;
import com.giri.oms.order.repository.OrderRepository;
import com.giri.oms.payment.dto.PaymentRequest;
import com.giri.oms.payment.dto.PaymentResponse;
import com.giri.oms.payment.entity.Payment;
import com.giri.oms.payment.entity.PaymentMethod;
import com.giri.oms.payment.entity.PaymentStatus;
import com.giri.oms.payment.exception.IllegalPaymentStateException;
import com.giri.oms.payment.exception.PaymentNotFoundException;
import com.giri.oms.payment.mapper.PaymentMapper;
import com.giri.oms.payment.repository.PaymentRepository;
import com.giri.oms.payment.service.impl.PaymentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests — no Spring context, no DB. Repository, OrderRepository, and
 * mapper are all mocked so these run in milliseconds and only exercise
 * PaymentServiceImpl's own logic.
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentMapper paymentMapper;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private Order order;
    private Payment payment;
    private PaymentRequest paymentRequest;
    private PaymentResponse paymentResponse;

    @BeforeEach
    void setUp() {
        Customer customer = new Customer();
        customer.setId(1L);
        customer.setFirstName("Ada");
        customer.setLastName("Lovelace");
        customer.setEmail("ada@example.com");
        customer.setStatus(CustomerStatus.ACTIVE);

        order = new Order();
        order.setId(1L);
        order.setCustomer(customer);
        order.setStatus(OrderStatus.PENDING);
        order.setTotalAmount(new BigDecimal("77.97"));

        payment = new Payment();
        payment.setId(1L);
        payment.setOrder(order);
        payment.setAmount(new BigDecimal("77.97"));
        payment.setMethod(PaymentMethod.CREDIT_CARD);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setCreatedAt(LocalDateTime.now());
        payment.setUpdatedAt(LocalDateTime.now());

        paymentRequest = new PaymentRequest(1L, new BigDecimal("77.97"), PaymentMethod.CREDIT_CARD);

        paymentResponse = new PaymentResponse(
                1L, 1L, new BigDecimal("77.97"), PaymentMethod.CREDIT_CARD, PaymentStatus.PENDING,
                null, LocalDateTime.now(), LocalDateTime.now());
    }

    @Nested
    class CreatePayment {

        @Test
        void savesAndReturnsMappedResponse() {
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(paymentRepository.save(any(Payment.class))).thenReturn(payment);
            when(paymentMapper.mapToPaymentResponse(payment)).thenReturn(paymentResponse);

            PaymentResponse result = paymentService.createPayment(paymentRequest);

            assertThat(result.getAmount()).isEqualByComparingTo("77.97");
            assertThat(result.getStatus()).isEqualTo(PaymentStatus.PENDING);
        }

        @Test
        void savesPaymentWithPendingStatusRegardlessOfInput() {
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(paymentMapper.mapToPaymentResponse(any(Payment.class))).thenReturn(paymentResponse);

            paymentService.createPayment(paymentRequest);

            var paymentCaptor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository).save(paymentCaptor.capture());
            assertThat(paymentCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(paymentCaptor.getValue().getOrder()).isEqualTo(order);
        }

        @Test
        void throwsOrderNotFoundException_whenOrderDoesNotExist() {
            when(orderRepository.findById(99L)).thenReturn(Optional.empty());
            paymentRequest.setOrderId(99L);

            assertThatThrownBy(() -> paymentService.createPayment(paymentRequest))
                    .isInstanceOf(OrderNotFoundException.class)
                    .hasMessageContaining("99");

            verify(paymentRepository, never()).save(any());
        }
    }

    @Nested
    class GetPaymentById {

        @Test
        void returnsMappedResponse_whenPaymentExists() {
            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
            when(paymentMapper.mapToPaymentResponse(payment)).thenReturn(paymentResponse);

            PaymentResponse result = paymentService.getPaymentById(1L);

            assertThat(result).isEqualTo(paymentResponse);
        }

        @Test
        void throwsPaymentNotFoundException_whenPaymentDoesNotExist() {
            when(paymentRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.getPaymentById(99L))
                    .isInstanceOf(PaymentNotFoundException.class)
                    .hasMessageContaining("99");

            verify(paymentMapper, never()).mapToPaymentResponse(any());
        }
    }

    @Nested
    class GetAllPayments {

        @Test
        void returnsPagedResponse_whenSortFieldIsValid() {
            Page<Payment> paymentPage = new PageImpl<>(List.of(payment));
            when(paymentRepository.findAll(any(Pageable.class))).thenReturn(paymentPage);
            when(paymentMapper.mapToPaymentResponse(payment)).thenReturn(paymentResponse);

            PagedResponse<PaymentResponse> result = paymentService.getAllPayments(0, 10, "status", "asc");

            assertThat(result.getContent()).containsExactly(paymentResponse);
            assertThat(result.getTotalElements()).isEqualTo(1);
        }

        @Test
        void throwsInvalidSortFieldException_whenSortFieldIsNotAllowed() {
            assertThatThrownBy(() -> paymentService.getAllPayments(0, 10, "secretInternalField", "asc"))
                    .isInstanceOf(InvalidSortFieldException.class)
                    .hasMessageContaining("secretInternalField");

            verifyNoInteractions(paymentRepository);
        }
    }

    @Nested
    class UpdatePaymentStatus {

        @Test
        void transitionsAndReturnsMappedResponse_whenTransitionIsAllowed() {
            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
            when(paymentRepository.save(payment)).thenReturn(payment);
            when(paymentMapper.mapToPaymentResponse(payment)).thenReturn(paymentResponse);

            paymentService.updatePaymentStatus(1L, PaymentStatus.COMPLETED, null);

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
            verify(paymentRepository).save(payment);
        }

        @Test
        void setsTransactionReference_whenProvided() {
            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
            when(paymentRepository.save(payment)).thenReturn(payment);
            when(paymentMapper.mapToPaymentResponse(payment)).thenReturn(paymentResponse);

            paymentService.updatePaymentStatus(1L, PaymentStatus.COMPLETED, "txn_9f8c3d2a");

            assertThat(payment.getTransactionReference()).isEqualTo("txn_9f8c3d2a");
        }

        @Test
        void leavesTransactionReferenceUnchanged_whenNotProvided() {
            payment.setTransactionReference("txn_existing");
            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
            when(paymentRepository.save(payment)).thenReturn(payment);
            when(paymentMapper.mapToPaymentResponse(payment)).thenReturn(paymentResponse);

            paymentService.updatePaymentStatus(1L, PaymentStatus.COMPLETED, null);

            assertThat(payment.getTransactionReference()).isEqualTo("txn_existing");
        }

        @Test
        void throwsPaymentNotFoundException_whenPaymentDoesNotExist() {
            when(paymentRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.updatePaymentStatus(99L, PaymentStatus.COMPLETED, null))
                    .isInstanceOf(PaymentNotFoundException.class);

            verify(paymentRepository, never()).save(any());
        }

        @Test
        void throwsIllegalPaymentStateException_whenTransitioningFromPendingToRefunded() {
            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment)); // payment starts PENDING

            assertThatThrownBy(() -> paymentService.updatePaymentStatus(1L, PaymentStatus.REFUNDED, null))
                    .isInstanceOf(IllegalPaymentStateException.class)
                    .hasMessageContaining("PENDING");

            verify(paymentRepository, never()).save(any());
        }

        @ParameterizedTest
        @EnumSource(value = PaymentStatus.class, names = {"FAILED", "REFUNDED"})
        void throwsIllegalPaymentStateException_whenTransitioningAwayFromTerminalState(PaymentStatus terminalStatus) {
            payment.setStatus(terminalStatus);
            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

            assertThatThrownBy(() -> paymentService.updatePaymentStatus(1L, PaymentStatus.PENDING, null))
                    .isInstanceOf(IllegalPaymentStateException.class);

            verify(paymentRepository, never()).save(any());
        }
    }

    @Nested
    class DeletePayment {

        @Test
        void deletesPayment_whenStatusIsPending() {
            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment)); // PENDING

            paymentService.deletePayment(1L);

            verify(paymentRepository).deleteById(1L);
        }

        @Test
        void deletesPayment_whenStatusIsFailed() {
            payment.setStatus(PaymentStatus.FAILED);
            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

            paymentService.deletePayment(1L);

            verify(paymentRepository).deleteById(1L);
        }

        @Test
        void throwsPaymentNotFoundException_whenPaymentDoesNotExist() {
            when(paymentRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.deletePayment(99L))
                    .isInstanceOf(PaymentNotFoundException.class);

            verify(paymentRepository, never()).deleteById(anyLong());
        }

        @ParameterizedTest
        @EnumSource(value = PaymentStatus.class, names = {"COMPLETED", "REFUNDED"})
        void throwsIllegalPaymentStateException_whenStatusDoesNotAllowDeletion(PaymentStatus nonDeletableStatus) {
            payment.setStatus(nonDeletableStatus);
            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

            assertThatThrownBy(() -> paymentService.deletePayment(1L))
                    .isInstanceOf(IllegalPaymentStateException.class);

            verify(paymentRepository, never()).deleteById(anyLong());
        }
    }

    @Nested
    class SearchPayments {

        @Test
        void delegatesToRepositoryAndMapsResults() {
            Page<Payment> paymentPage = new PageImpl<>(List.of(payment));
            Pageable pageable = PageRequest.of(0, 10); // unsorted

            when(paymentRepository.searchPayments(1L, null, null, null, null, pageable))
                    .thenReturn(paymentPage);
            when(paymentMapper.mapToPaymentResponse(payment)).thenReturn(paymentResponse);

            Page<PaymentResponse> result = paymentService.searchPayments(1L, null, null, null, null, pageable);

            assertThat(result.getContent()).containsExactly(paymentResponse);
        }

        @Test
        void normalizesSortFieldCaseBeforeDelegatingToRepository() {
            Pageable requestedPageable = PageRequest.of(0, 10, Sort.by("STATUS").ascending());
            when(paymentRepository.searchPayments(any(), any(), any(), any(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of(payment)));
            when(paymentMapper.mapToPaymentResponse(any())).thenReturn(paymentResponse);

            paymentService.searchPayments(null, null, null, null, null, requestedPageable);

            var pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            verify(paymentRepository).searchPayments(any(), any(), any(), any(), any(), pageableCaptor.capture());
            Sort.Order sortOrder = pageableCaptor.getValue().getSort().getOrderFor("status");
            assertThat(sortOrder).isNotNull();
            assertThat(sortOrder.isAscending()).isTrue();
        }

        @Test
        void throwsInvalidSortFieldException_whenSortFieldNotOnAllowList() {
            Pageable requestedPageable = PageRequest.of(0, 10, Sort.by("bogusField").ascending());

            assertThatThrownBy(() -> paymentService.searchPayments(null, null, null, null, null, requestedPageable))
                    .isInstanceOf(InvalidSortFieldException.class)
                    .hasMessageContaining("bogusField");

            verifyNoInteractions(paymentRepository);
        }
    }

    @Nested
    class SearchPaymentsBySpecification {

        @Test
        void delegatesToRepositoryFindAllWithSpecAndMapsResults() {
            Page<Payment> paymentPage = new PageImpl<>(List.of(payment));
            when(paymentRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class)))
                    .thenReturn(paymentPage);
            when(paymentMapper.mapToPaymentResponse(payment)).thenReturn(paymentResponse);

            Page<PaymentResponse> result = paymentService.searchPaymentsBySpecification(
                    1L, PaymentStatus.PENDING, PaymentMethod.CREDIT_CARD,
                    new BigDecimal("50"), new BigDecimal("100"), PageRequest.of(0, 10));

            assertThat(result.getContent()).containsExactly(paymentResponse);
        }
    }
}
