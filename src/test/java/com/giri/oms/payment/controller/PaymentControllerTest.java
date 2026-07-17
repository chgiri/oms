package com.giri.oms.payment.controller;

import tools.jackson.databind.json.JsonMapper;
import com.giri.oms.common.dto.PagedResponse;
import com.giri.oms.order.exception.OrderNotFoundException;
import com.giri.oms.payment.dto.PaymentRequest;
import com.giri.oms.payment.dto.PaymentResponse;
import com.giri.oms.payment.dto.PaymentStatusUpdateRequest;
import com.giri.oms.payment.entity.PaymentMethod;
import com.giri.oms.payment.entity.PaymentStatus;
import com.giri.oms.payment.exception.IllegalPaymentStateException;
import com.giri.oms.payment.exception.PaymentNotFoundException;
import com.giri.oms.payment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * @WebMvcTest loads only the web layer (this controller + @ControllerAdvice
 * classes, auto-detected — no explicit @Import needed) — the service is
 * mocked, so this verifies HTTP status codes, JSON shape, Bean Validation
 * triggering, and exception-handler wiring, without touching the DB or
 * business logic.
 */
@WebMvcTest(PaymentController.class)
@AutoConfigureMockMvc(addFilters = false) // security is tested separately (see SecurityIntegrationTest) - this slice only exercises controller/validation/exception-handling logic
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @MockitoBean
    private PaymentService paymentService;

    private PaymentResponse paymentResponse;
    private PaymentRequest validRequest;

    @BeforeEach
    void setUp() {
        paymentResponse = new PaymentResponse(
                1L, 1L, new BigDecimal("77.97"), PaymentMethod.CREDIT_CARD, PaymentStatus.PENDING,
                null, LocalDateTime.now(), LocalDateTime.now());

        validRequest = new PaymentRequest(1L, new BigDecimal("77.97"), PaymentMethod.CREDIT_CARD);
    }

    @Nested
    class CreatePayment {

        @Test
        void returns201AndBody_whenRequestIsValid() throws Exception {
            when(paymentService.createPayment(any())).thenReturn(paymentResponse);

            mockMvc.perform(post("/api/payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.orderId").value(1))
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.method").value("CREDIT_CARD"));
        }

        @Test
        void returns400_whenOrderIdIsMissing() throws Exception {
            validRequest.setOrderId(null);

            mockMvc.perform(post("/api/payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.orderId").exists());
        }

        @Test
        void returns400_whenAmountIsMissing() throws Exception {
            validRequest.setAmount(null);

            mockMvc.perform(post("/api/payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.amount").exists());
        }

        @Test
        void returns400_whenAmountIsNotPositive() throws Exception {
            validRequest.setAmount(BigDecimal.ZERO);

            mockMvc.perform(post("/api/payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void returns400_whenMethodIsMissing() throws Exception {
            validRequest.setMethod(null);

            mockMvc.perform(post("/api/payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.method").exists());
        }

        @Test
        void returns404_whenOrderDoesNotExist() throws Exception {
            when(paymentService.createPayment(any())).thenThrow(new OrderNotFoundException(99L));

            mockMvc.perform(post("/api/payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class GetPaymentById {

        @Test
        void returns200AndBody_whenPaymentExists() throws Exception {
            when(paymentService.getPaymentById(1L)).thenReturn(paymentResponse);

            mockMvc.perform(get("/api/payments/{id}", 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.orderId").value(1));
        }

        @Test
        void returns404_whenPaymentDoesNotExist() throws Exception {
            when(paymentService.getPaymentById(99L)).thenThrow(new PaymentNotFoundException(99L));

            mockMvc.perform(get("/api/payments/{id}", 99L))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("99")));
        }
    }

    @Nested
    class GetAllPayments {

        @Test
        void returns200AndPagedResponse() throws Exception {
            PagedResponse<PaymentResponse> paged = new PagedResponse<>(
                    List.of(paymentResponse), 0, 10, 1, 1, true);
            when(paymentService.getAllPayments(0, 10, "id", "asc")).thenReturn(paged);

            mockMvc.perform(get("/api/payments"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].orderId").value(1))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }
    }

    @Nested
    class UpdatePaymentStatus {

        @Test
        void returns200_whenTransitionIsValid() throws Exception {
            PaymentStatusUpdateRequest request = new PaymentStatusUpdateRequest(PaymentStatus.COMPLETED, "txn_9f8c3d2a");
            PaymentResponse completedResponse = new PaymentResponse(
                    1L, 1L, new BigDecimal("77.97"), PaymentMethod.CREDIT_CARD, PaymentStatus.COMPLETED,
                    "txn_9f8c3d2a", LocalDateTime.now(), LocalDateTime.now());
            when(paymentService.updatePaymentStatus(eq(1L), eq(PaymentStatus.COMPLETED), eq("txn_9f8c3d2a")))
                    .thenReturn(completedResponse);

            mockMvc.perform(patch("/api/payments/{id}/status", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.transactionReference").value("txn_9f8c3d2a"));
        }

        @Test
        void returns400_whenStatusIsMissing() throws Exception {
            mockMvc.perform(patch("/api/payments/{id}/status", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void returns404_whenPaymentDoesNotExist() throws Exception {
            PaymentStatusUpdateRequest request = new PaymentStatusUpdateRequest(PaymentStatus.COMPLETED, null);
            when(paymentService.updatePaymentStatus(eq(99L), any(), any())).thenThrow(new PaymentNotFoundException(99L));

            mockMvc.perform(patch("/api/payments/{id}/status", 99L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void returns409_whenTransitionIsIllegal() throws Exception {
            PaymentStatusUpdateRequest request = new PaymentStatusUpdateRequest(PaymentStatus.REFUNDED, null);
            when(paymentService.updatePaymentStatus(eq(1L), eq(PaymentStatus.REFUNDED), any()))
                    .thenThrow(new IllegalPaymentStateException("Cannot transition payment id 1 from status PENDING to REFUNDED"));

            mockMvc.perform(patch("/api/payments/{id}/status", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    class DeletePayment {

        @Test
        void returns204_whenPaymentIsDeleted() throws Exception {
            mockMvc.perform(delete("/api/payments/{id}", 1L))
                    .andExpect(status().isNoContent());
        }

        @Test
        void returns404_whenPaymentDoesNotExist() throws Exception {
            org.mockito.Mockito.doThrow(new PaymentNotFoundException(99L))
                    .when(paymentService).deletePayment(99L);

            mockMvc.perform(delete("/api/payments/{id}", 99L))
                    .andExpect(status().isNotFound());
        }

        @Test
        void returns409_whenPaymentStatusDoesNotAllowDeletion() throws Exception {
            org.mockito.Mockito.doThrow(new IllegalPaymentStateException("Payment id 1 cannot be deleted while in status COMPLETED"))
                    .when(paymentService).deletePayment(1L);

            mockMvc.perform(delete("/api/payments/{id}", 1L))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    class SearchPayments {

        @Test
        void returns200AndFiltersByQueryParams_jpqlApproach() throws Exception {
            Page<PaymentResponse> page = new PageImpl<>(List.of(paymentResponse), PageRequest.of(0, 10), 1);
            when(paymentService.searchPayments(eq(1L), any(), any(), any(), any(), any()))
                    .thenReturn(page);

            mockMvc.perform(get("/api/payments/search").param("orderId", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].orderId").value(1));
        }

        @Test
        void returns200AndFiltersByStatus() throws Exception {
            Page<PaymentResponse> page = new PageImpl<>(List.of(paymentResponse), PageRequest.of(0, 10), 1);
            when(paymentService.searchPayments(any(), eq(PaymentStatus.PENDING), any(), any(), any(), any()))
                    .thenReturn(page);

            mockMvc.perform(get("/api/payments/search").param("status", "PENDING"))
                    .andExpect(status().isOk());
        }

        @Test
        void returns200AndFiltersByQueryParams_specificationApproach() throws Exception {
            Page<PaymentResponse> page = new PageImpl<>(List.of(paymentResponse), PageRequest.of(0, 10), 1);
            when(paymentService.searchPaymentsBySpecification(eq(1L), any(), any(), any(), any(), any()))
                    .thenReturn(page);

            mockMvc.perform(get("/api/payments/search/advanced").param("orderId", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].orderId").value(1));
        }
    }
}
