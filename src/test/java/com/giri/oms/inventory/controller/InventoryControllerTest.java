package com.giri.oms.inventory.controller;

import tools.jackson.databind.json.JsonMapper;
import com.giri.oms.common.dto.PagedResponse;
import com.giri.oms.inventory.dto.InventoryRequest;
import com.giri.oms.inventory.dto.InventoryResponse;
import com.giri.oms.inventory.exception.InventoryAlreadyExistsException;
import com.giri.oms.inventory.exception.InventoryNotFoundException;
import com.giri.oms.inventory.service.InventoryService;
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
@WebMvcTest(InventoryController.class)
class InventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @MockitoBean
    private InventoryService inventoryService;

    private InventoryResponse inventoryResponse;
    private InventoryRequest validRequest;

    @BeforeEach
    void setUp() {
        inventoryResponse = new InventoryResponse(
                1L, 1L, "Wireless Mouse", "WH-EAST-01",
                120, 15, 20, LocalDateTime.now(), LocalDateTime.now());

        validRequest = new InventoryRequest();
        validRequest.setProductId(1L);
        validRequest.setLocation("WH-EAST-01");
        validRequest.setQuantityAvailable(120);
        validRequest.setQuantityReserved(15);
        validRequest.setReorderLevel(20);
    }

    @Nested
    class CreateInventory {

        @Test
        void returns201AndBody_whenRequestIsValid() throws Exception {
            when(inventoryService.createInventory(any())).thenReturn(inventoryResponse);

            mockMvc.perform(post("/api/inventory")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.productName").value("Wireless Mouse"))
                    .andExpect(jsonPath("$.location").value("WH-EAST-01"));
        }

        @Test
        void returns400_whenProductIdIsMissing() throws Exception {
            validRequest.setProductId(null);

            mockMvc.perform(post("/api/inventory")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.productId").exists());
        }

        @Test
        void returns400_whenLocationIsBlank() throws Exception {
            validRequest.setLocation("");

            mockMvc.perform(post("/api/inventory")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.location").exists());
        }

        @Test
        void returns400_whenQuantityAvailableIsNegative() throws Exception {
            validRequest.setQuantityAvailable(-5);

            mockMvc.perform(post("/api/inventory")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.quantityAvailable").exists());
        }

        @Test
        void returns404_whenProductDoesNotExist() throws Exception {
            when(inventoryService.createInventory(any())).thenThrow(new ProductNotFoundException(99L));

            mockMvc.perform(post("/api/inventory")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void returns409_whenProductLocationPairAlreadyExists() throws Exception {
            when(inventoryService.createInventory(any()))
                    .thenThrow(new InventoryAlreadyExistsException(1L, "WH-EAST-01"));

            mockMvc.perform(post("/api/inventory")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    class GetInventoryById {

        @Test
        void returns200AndBody_whenInventoryExists() throws Exception {
            when(inventoryService.getInventoryById(1L)).thenReturn(inventoryResponse);

            mockMvc.perform(get("/api/inventory/{id}", 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.location").value("WH-EAST-01"));
        }

        @Test
        void returns404_whenInventoryDoesNotExist() throws Exception {
            when(inventoryService.getInventoryById(99L)).thenThrow(new InventoryNotFoundException(99L));

            mockMvc.perform(get("/api/inventory/{id}", 99L))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("99")));
        }
    }

    @Nested
    class GetAllInventory {

        @Test
        void returns200AndPagedResponse() throws Exception {
            PagedResponse<InventoryResponse> paged = new PagedResponse<>(
                    List.of(inventoryResponse), 0, 10, 1, 1, true);
            when(inventoryService.getAllInventory(0, 10, "id", "asc")).thenReturn(paged);

            mockMvc.perform(get("/api/inventory"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].location").value("WH-EAST-01"))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }
    }

    @Nested
    class UpdateInventory {

        @Test
        void returns200_whenRequestIsValid() throws Exception {
            when(inventoryService.updateInventory(eq(1L), any())).thenReturn(inventoryResponse);

            mockMvc.perform(put("/api/inventory/{id}", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.location").value("WH-EAST-01"));
        }

        @Test
        void returns404_whenInventoryDoesNotExist() throws Exception {
            when(inventoryService.updateInventory(eq(99L), any())).thenThrow(new InventoryNotFoundException(99L));

            mockMvc.perform(put("/api/inventory/{id}", 99L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void returns409_whenChangingToAnotherRecordsProductLocation() throws Exception {
            when(inventoryService.updateInventory(eq(1L), any()))
                    .thenThrow(new InventoryAlreadyExistsException(1L, "WH-WEST-02"));

            mockMvc.perform(put("/api/inventory/{id}", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isConflict());
        }

        @Test
        void returns400_whenRequestFailsValidation() throws Exception {
            validRequest.setLocation(null);

            mockMvc.perform(put("/api/inventory/{id}", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class DeleteInventory {

        @Test
        void returns204_whenInventoryIsDeleted() throws Exception {
            mockMvc.perform(delete("/api/inventory/{id}", 1L))
                    .andExpect(status().isNoContent());
        }

        @Test
        void returns404_whenInventoryDoesNotExist() throws Exception {
            org.mockito.Mockito.doThrow(new InventoryNotFoundException(99L))
                    .when(inventoryService).deleteInventory(99L);

            mockMvc.perform(delete("/api/inventory/{id}", 99L))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class SearchInventory {

        @Test
        void returns200AndFiltersByQueryParams_jpqlApproach() throws Exception {
            Page<InventoryResponse> page = new PageImpl<>(List.of(inventoryResponse), PageRequest.of(0, 10), 1);
            when(inventoryService.searchInventory(eq(1L), any(), anyBoolean(), any()))
                    .thenReturn(page);

            mockMvc.perform(get("/api/inventory/search").param("productId", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].location").value("WH-EAST-01"));
        }

        @Test
        void returns200AndFiltersByLowStockOnly() throws Exception {
            Page<InventoryResponse> page = new PageImpl<>(List.of(inventoryResponse), PageRequest.of(0, 10), 1);
            when(inventoryService.searchInventory(any(), any(), eq(true), any()))
                    .thenReturn(page);

            mockMvc.perform(get("/api/inventory/search").param("lowStockOnly", "true"))
                    .andExpect(status().isOk());
        }

        @Test
        void returns200AndFiltersByQueryParams_specificationApproach() throws Exception {
            Page<InventoryResponse> page = new PageImpl<>(List.of(inventoryResponse), PageRequest.of(0, 10), 1);
            when(inventoryService.searchInventoryBySpecification(eq(1L), any(), anyBoolean(), any()))
                    .thenReturn(page);

            mockMvc.perform(get("/api/inventory/search/advanced").param("productId", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].location").value("WH-EAST-01"));
        }
    }
}
