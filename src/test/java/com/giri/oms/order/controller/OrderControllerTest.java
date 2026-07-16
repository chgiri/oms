package com.giri.oms.order.controller;

import tools.jackson.databind.json.JsonMapper;
import com.giri.oms.common.dto.PagedResponse;
import com.giri.oms.customer.exception.CustomerNotFoundException;
import com.giri.oms.order.dto.OrderItemRequest;
import com.giri.oms.order.dto.OrderItemResponse;
import com.giri.oms.order.dto.OrderRequest;
import com.giri.oms.order.dto.OrderResponse;
import com.giri.oms.order.dto.OrderStatusUpdateRequest;
import com.giri.oms.order.entity.OrderStatus;
import com.giri.oms.order.exception.IllegalOrderStateException;
import com.giri.oms.order.exception.OrderNotFoundException;
import com.giri.oms.order.service.OrderService;
import com.giri.oms.product.exception.ProductNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @MockitoBean
    private OrderService orderService;

    private OrderResponse orderResponse;
    private OrderRequest validRequest;

    @BeforeEach
    void setUp() {
        OrderItemResponse itemResponse = new OrderItemResponse(
                1L, 1L, "Wireless Mouse", 3, new BigDecimal("25.99"), new BigDecimal("77.97"));

        orderResponse = new OrderResponse(
                1L, 1L, "Ada Lovelace", OrderStatus.PENDING, new BigDecimal("77.97"),
                List.of(itemResponse), LocalDateTime.now(), LocalDateTime.now());

        validRequest = new OrderRequest(1L, List.of(new OrderItemRequest(1L, 3)));
    }

    @Nested
    class CreateOrder {

        @Test
        void returns201AndBody_whenRequestIsValid() throws Exception {
            when(orderService.createOrder(any())).thenReturn(orderResponse);

            mockMvc.perform(post("/api/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.customerName").value("Ada Lovelace"))
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.items[0].productName").value("Wireless Mouse"));
        }

        @Test
        void returns400_whenCustomerIdIsMissing() throws Exception {
            validRequest.setCustomerId(null);

            mockMvc.perform(post("/api/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.customerId").exists());
        }

        @Test
        void returns400_whenItemsListIsEmpty() throws Exception {
            validRequest.setItems(List.of());

            mockMvc.perform(post("/api/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.items").exists());
        }

        @Test
        void returns400_whenQuantityIsNotPositive() throws Exception {
            validRequest.setItems(List.of(new OrderItemRequest(1L, 0)));

            mockMvc.perform(post("/api/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void returns404_whenCustomerDoesNotExist() throws Exception {
            when(orderService.createOrder(any())).thenThrow(new CustomerNotFoundException(99L));

            mockMvc.perform(post("/api/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void returns404_whenAnItemsProductDoesNotExist() throws Exception {
            when(orderService.createOrder(any())).thenThrow(new ProductNotFoundException(99L));

            mockMvc.perform(post("/api/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class GetOrderById {

        @Test
        void returns200AndBody_whenOrderExists() throws Exception {
            when(orderService.getOrderById(1L)).thenReturn(orderResponse);

            mockMvc.perform(get("/api/orders/{id}", 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.customerName").value("Ada Lovelace"));
        }

        @Test
        void returns404_whenOrderDoesNotExist() throws Exception {
            when(orderService.getOrderById(99L)).thenThrow(new OrderNotFoundException(99L));

            mockMvc.perform(get("/api/orders/{id}", 99L))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("99")));
        }
    }

    @Nested
    class GetAllOrders {

        @Test
        void returns200AndPagedResponse() throws Exception {
            PagedResponse<OrderResponse> paged = new PagedResponse<>(
                    List.of(orderResponse), 0, 10, 1, 1, true);
            when(orderService.getAllOrders(0, 10, "id", "asc")).thenReturn(paged);

            mockMvc.perform(get("/api/orders"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].customerName").value("Ada Lovelace"))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }
    }

    @Nested
    class UpdateOrderStatus {

        @Test
        void returns200_whenTransitionIsValid() throws Exception {
            OrderStatusUpdateRequest request = new OrderStatusUpdateRequest(OrderStatus.CONFIRMED);
            OrderResponse confirmedResponse = new OrderResponse(
                    1L, 1L, "Ada Lovelace", OrderStatus.CONFIRMED, new BigDecimal("77.97"),
                    orderResponse.getItems(), LocalDateTime.now(), LocalDateTime.now());
            when(orderService.updateOrderStatus(eq(1L), eq(OrderStatus.CONFIRMED))).thenReturn(confirmedResponse);

            mockMvc.perform(patch("/api/orders/{id}/status", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CONFIRMED"));
        }

        @Test
        void returns400_whenStatusIsMissing() throws Exception {
            mockMvc.perform(patch("/api/orders/{id}/status", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void returns404_whenOrderDoesNotExist() throws Exception {
            OrderStatusUpdateRequest request = new OrderStatusUpdateRequest(OrderStatus.CONFIRMED);
            when(orderService.updateOrderStatus(eq(99L), any())).thenThrow(new OrderNotFoundException(99L));

            mockMvc.perform(patch("/api/orders/{id}/status", 99L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void returns409_whenTransitionIsIllegal() throws Exception {
            OrderStatusUpdateRequest request = new OrderStatusUpdateRequest(OrderStatus.DELIVERED);
            when(orderService.updateOrderStatus(eq(1L), eq(OrderStatus.DELIVERED)))
                    .thenThrow(new IllegalOrderStateException("Cannot transition order id 1 from status PENDING to DELIVERED"));

            mockMvc.perform(patch("/api/orders/{id}/status", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    class DeleteOrder {

        @Test
        void returns204_whenOrderIsDeleted() throws Exception {
            mockMvc.perform(delete("/api/orders/{id}", 1L))
                    .andExpect(status().isNoContent());
        }

        @Test
        void returns404_whenOrderDoesNotExist() throws Exception {
            org.mockito.Mockito.doThrow(new OrderNotFoundException(99L))
                    .when(orderService).deleteOrder(99L);

            mockMvc.perform(delete("/api/orders/{id}", 99L))
                    .andExpect(status().isNotFound());
        }

        @Test
        void returns409_whenOrderStatusDoesNotAllowDeletion() throws Exception {
            org.mockito.Mockito.doThrow(new IllegalOrderStateException("Order id 1 cannot be deleted while in status SHIPPED"))
                    .when(orderService).deleteOrder(1L);

            mockMvc.perform(delete("/api/orders/{id}", 1L))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    class SearchOrders {

        @Test
        void returns200AndFiltersByQueryParams_jpqlApproach() throws Exception {
            Page<OrderResponse> page = new PageImpl<>(List.of(orderResponse), PageRequest.of(0, 10), 1);
            when(orderService.searchOrders(eq(1L), any(), any(), any(), any()))
                    .thenReturn(page);

            mockMvc.perform(get("/api/orders/search").param("customerId", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].customerName").value("Ada Lovelace"));
        }

        @Test
        void returns200AndFiltersByStatus() throws Exception {
            Page<OrderResponse> page = new PageImpl<>(List.of(orderResponse), PageRequest.of(0, 10), 1);
            when(orderService.searchOrders(any(), eq(OrderStatus.PENDING), any(), any(), any()))
                    .thenReturn(page);

            mockMvc.perform(get("/api/orders/search").param("status", "PENDING"))
                    .andExpect(status().isOk());
        }

        @Test
        void returns200AndFiltersByQueryParams_specificationApproach() throws Exception {
            Page<OrderResponse> page = new PageImpl<>(List.of(orderResponse), PageRequest.of(0, 10), 1);
            when(orderService.searchOrdersBySpecification(eq(1L), any(), any(), any(), any()))
                    .thenReturn(page);

            mockMvc.perform(get("/api/orders/search/advanced").param("customerId", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].customerName").value("Ada Lovelace"));
        }
    }
}
