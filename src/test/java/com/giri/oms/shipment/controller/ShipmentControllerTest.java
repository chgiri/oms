package com.giri.oms.shipment.controller;

import tools.jackson.databind.json.JsonMapper;
import com.giri.oms.common.dto.PagedResponse;
import com.giri.oms.order.exception.OrderNotFoundException;
import com.giri.oms.shipment.dto.ShipmentRequest;
import com.giri.oms.shipment.dto.ShipmentResponse;
import com.giri.oms.shipment.dto.ShipmentStatusUpdateRequest;
import com.giri.oms.shipment.entity.ShipmentStatus;
import com.giri.oms.shipment.entity.ShippingCarrier;
import com.giri.oms.shipment.exception.IllegalShipmentStateException;
import com.giri.oms.shipment.exception.ShipmentNotFoundException;
import com.giri.oms.shipment.service.ShipmentService;
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
@WebMvcTest(ShipmentController.class)
class ShipmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @MockitoBean
    private ShipmentService shipmentService;

    private ShipmentResponse shipmentResponse;
    private ShipmentRequest validRequest;

    @BeforeEach
    void setUp() {
        shipmentResponse = new ShipmentResponse(
                1L, 1L, ShippingCarrier.UPS, ShipmentStatus.PENDING, null, null, null,
                LocalDateTime.now(), LocalDateTime.now());

        validRequest = new ShipmentRequest(1L, ShippingCarrier.UPS);
    }

    @Nested
    class CreateShipment {

        @Test
        void returns201AndBody_whenRequestIsValid() throws Exception {
            when(shipmentService.createShipment(any())).thenReturn(shipmentResponse);

            mockMvc.perform(post("/api/shipments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.orderId").value(1))
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.carrier").value("UPS"));
        }

        @Test
        void returns400_whenOrderIdIsMissing() throws Exception {
            validRequest.setOrderId(null);

            mockMvc.perform(post("/api/shipments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.orderId").exists());
        }

        @Test
        void returns400_whenCarrierIsMissing() throws Exception {
            validRequest.setCarrier(null);

            mockMvc.perform(post("/api/shipments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.carrier").exists());
        }

        @Test
        void returns404_whenOrderDoesNotExist() throws Exception {
            when(shipmentService.createShipment(any())).thenThrow(new OrderNotFoundException(99L));

            mockMvc.perform(post("/api/shipments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class GetShipmentById {

        @Test
        void returns200AndBody_whenShipmentExists() throws Exception {
            when(shipmentService.getShipmentById(1L)).thenReturn(shipmentResponse);

            mockMvc.perform(get("/api/shipments/{id}", 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.orderId").value(1));
        }

        @Test
        void returns404_whenShipmentDoesNotExist() throws Exception {
            when(shipmentService.getShipmentById(99L)).thenThrow(new ShipmentNotFoundException(99L));

            mockMvc.perform(get("/api/shipments/{id}", 99L))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("99")));
        }
    }

    @Nested
    class GetAllShipments {

        @Test
        void returns200AndPagedResponse() throws Exception {
            PagedResponse<ShipmentResponse> paged = new PagedResponse<>(
                    List.of(shipmentResponse), 0, 10, 1, 1, true);
            when(shipmentService.getAllShipments(0, 10, "id", "asc")).thenReturn(paged);

            mockMvc.perform(get("/api/shipments"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].orderId").value(1))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }
    }

    @Nested
    class UpdateShipmentStatus {

        @Test
        void returns200_whenTransitionIsValid() throws Exception {
            ShipmentStatusUpdateRequest request = new ShipmentStatusUpdateRequest(ShipmentStatus.SHIPPED, "1Z999AA10123456784");
            ShipmentResponse shippedResponse = new ShipmentResponse(
                    1L, 1L, ShippingCarrier.UPS, ShipmentStatus.SHIPPED, "1Z999AA10123456784",
                    LocalDateTime.now(), null, LocalDateTime.now(), LocalDateTime.now());
            when(shipmentService.updateShipmentStatus(eq(1L), eq(ShipmentStatus.SHIPPED), eq("1Z999AA10123456784")))
                    .thenReturn(shippedResponse);

            mockMvc.perform(patch("/api/shipments/{id}/status", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("SHIPPED"))
                    .andExpect(jsonPath("$.trackingNumber").value("1Z999AA10123456784"));
        }

        @Test
        void returns400_whenStatusIsMissing() throws Exception {
            mockMvc.perform(patch("/api/shipments/{id}/status", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void returns404_whenShipmentDoesNotExist() throws Exception {
            ShipmentStatusUpdateRequest request = new ShipmentStatusUpdateRequest(ShipmentStatus.SHIPPED, null);
            when(shipmentService.updateShipmentStatus(eq(99L), any(), any())).thenThrow(new ShipmentNotFoundException(99L));

            mockMvc.perform(patch("/api/shipments/{id}/status", 99L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void returns409_whenTransitionIsIllegal() throws Exception {
            ShipmentStatusUpdateRequest request = new ShipmentStatusUpdateRequest(ShipmentStatus.DELIVERED, null);
            when(shipmentService.updateShipmentStatus(eq(1L), eq(ShipmentStatus.DELIVERED), any()))
                    .thenThrow(new IllegalShipmentStateException("Cannot transition shipment id 1 from status PENDING to DELIVERED"));

            mockMvc.perform(patch("/api/shipments/{id}/status", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    class DeleteShipment {

        @Test
        void returns204_whenShipmentIsDeleted() throws Exception {
            mockMvc.perform(delete("/api/shipments/{id}", 1L))
                    .andExpect(status().isNoContent());
        }

        @Test
        void returns404_whenShipmentDoesNotExist() throws Exception {
            org.mockito.Mockito.doThrow(new ShipmentNotFoundException(99L))
                    .when(shipmentService).deleteShipment(99L);

            mockMvc.perform(delete("/api/shipments/{id}", 99L))
                    .andExpect(status().isNotFound());
        }

        @Test
        void returns409_whenShipmentStatusDoesNotAllowDeletion() throws Exception {
            org.mockito.Mockito.doThrow(new IllegalShipmentStateException("Shipment id 1 cannot be deleted while in status SHIPPED"))
                    .when(shipmentService).deleteShipment(1L);

            mockMvc.perform(delete("/api/shipments/{id}", 1L))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    class SearchShipments {

        @Test
        void returns200AndFiltersByQueryParams_jpqlApproach() throws Exception {
            Page<ShipmentResponse> page = new PageImpl<>(List.of(shipmentResponse), PageRequest.of(0, 10), 1);
            when(shipmentService.searchShipments(eq(1L), eq(ShipmentStatus.PENDING), any(), any()))
                    .thenReturn(page);

            mockMvc.perform(get("/api/shipments/search")
                            .param("orderId", "1")
                            .param("status", "PENDING"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].orderId").value(1));
        }

        @Test
        void returns200AndFiltersByQueryParams_specificationApproach() throws Exception {
            Page<ShipmentResponse> page = new PageImpl<>(List.of(shipmentResponse), PageRequest.of(0, 10), 1);
            when(shipmentService.searchShipmentsBySpecification(eq(1L), eq(ShipmentStatus.PENDING), any(), any()))
                    .thenReturn(page);

            mockMvc.perform(get("/api/shipments/search/advanced")
                            .param("orderId", "1")
                            .param("status", "PENDING"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].orderId").value(1));
        }
    }
}
