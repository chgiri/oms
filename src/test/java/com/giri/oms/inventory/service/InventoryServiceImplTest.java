package com.giri.oms.inventory.service;

import com.giri.oms.common.dto.PagedResponse;
import com.giri.oms.common.exception.InvalidSortFieldException;
import com.giri.oms.common.lock.DistributedLockService;
import com.giri.oms.inventory.dto.InventoryRequest;
import com.giri.oms.inventory.dto.InventoryResponse;
import com.giri.oms.inventory.entity.Inventory;
import com.giri.oms.inventory.exception.InventoryAlreadyExistsException;
import com.giri.oms.inventory.exception.InventoryNotFoundException;
import com.giri.oms.inventory.mapper.InventoryMapper;
import com.giri.oms.inventory.repository.InventoryRepository;
import com.giri.oms.inventory.service.impl.InventoryServiceImpl;
import com.giri.oms.product.entity.Product;
import com.giri.oms.product.exception.ProductNotFoundException;
import com.giri.oms.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests — no Spring context, no DB. Repository, ProductRepository, and
 * mapper are all mocked so these run in milliseconds and only exercise
 * InventoryServiceImpl's own logic.
 */
@ExtendWith(MockitoExtension.class)
class InventoryServiceImplTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private InventoryMapper inventoryMapper;

    @Mock
    private DistributedLockService distributedLockService;

    @InjectMocks
    private InventoryServiceImpl inventoryService;

    private Product product;
    private Inventory inventory;
    private InventoryRequest inventoryRequest;
    private InventoryResponse inventoryResponse;

    @BeforeEach
    void setUp() {
        // updateInventory wraps its work in a distributed lock — for these unit tests
        // (no real Redis) just run the wrapped action straight through, same as the
        // real lock does once acquired.
        lenient().when(distributedLockService.executeWithLock(anyString(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    java.util.function.Supplier<?> action = invocation.getArgument(3);
                    return action.get();
                });

        product = new Product();
        product.setId(1L);
        product.setName("Wireless Mouse");
        product.setPrice(new BigDecimal("25.99"));

        inventory = new Inventory();
        inventory.setId(1L);
        inventory.setProduct(product);
        inventory.setLocation("WH-EAST-01");
        inventory.setQuantityAvailable(120);
        inventory.setQuantityReserved(15);
        inventory.setReorderLevel(20);
        inventory.setCreatedAt(LocalDateTime.now());
        inventory.setUpdatedAt(LocalDateTime.now());

        inventoryRequest = new InventoryRequest();
        inventoryRequest.setProductId(1L);
        inventoryRequest.setLocation("WH-EAST-01");
        inventoryRequest.setQuantityAvailable(120);
        inventoryRequest.setQuantityReserved(15);
        inventoryRequest.setReorderLevel(20);

        inventoryResponse = new InventoryResponse(
                1L, 1L, "Wireless Mouse", "WH-EAST-01",
                120, 15, 20, LocalDateTime.now(), LocalDateTime.now());
    }

    @Nested
    class CreateInventory {

        @Test
        void savesAndReturnsMappedResponse() {
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(inventoryRepository.existsByProductIdAndLocation(1L, "WH-EAST-01")).thenReturn(false);
            when(inventoryMapper.mapToInventory(inventoryRequest)).thenReturn(inventory);
            when(inventoryRepository.save(inventory)).thenReturn(inventory);
            when(inventoryMapper.mapToInventoryResponse(inventory)).thenReturn(inventoryResponse);

            InventoryResponse result = inventoryService.createInventory(inventoryRequest);

            assertThat(result).isEqualTo(inventoryResponse);
            verify(inventoryRepository).save(inventory);
        }

        @Test
        void throwsProductNotFoundException_whenProductDoesNotExist() {
            when(productRepository.findById(99L)).thenReturn(Optional.empty());
            inventoryRequest.setProductId(99L);

            assertThatThrownBy(() -> inventoryService.createInventory(inventoryRequest))
                    .isInstanceOf(ProductNotFoundException.class)
                    .hasMessageContaining("99");

            verify(inventoryRepository, never()).save(any());
        }

        @Test
        void throwsInventoryAlreadyExistsException_whenProductLocationPairIsTaken() {
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(inventoryRepository.existsByProductIdAndLocation(1L, "WH-EAST-01")).thenReturn(true);

            assertThatThrownBy(() -> inventoryService.createInventory(inventoryRequest))
                    .isInstanceOf(InventoryAlreadyExistsException.class)
                    .hasMessageContaining("WH-EAST-01");

            verify(inventoryRepository, never()).save(any());
        }
    }

    @Nested
    class GetInventoryById {

        @Test
        void returnsMappedResponse_whenInventoryExists() {
            when(inventoryRepository.findById(1L)).thenReturn(Optional.of(inventory));
            when(inventoryMapper.mapToInventoryResponse(inventory)).thenReturn(inventoryResponse);

            InventoryResponse result = inventoryService.getInventoryById(1L);

            assertThat(result).isEqualTo(inventoryResponse);
        }

        @Test
        void throwsInventoryNotFoundException_whenInventoryDoesNotExist() {
            when(inventoryRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> inventoryService.getInventoryById(99L))
                    .isInstanceOf(InventoryNotFoundException.class)
                    .hasMessageContaining("99");

            verify(inventoryMapper, never()).mapToInventoryResponse(any());
        }
    }

    @Nested
    class GetAllInventory {

        @Test
        void returnsPagedResponse_whenSortFieldIsValid() {
            Page<Inventory> inventoryPage = new PageImpl<>(List.of(inventory));
            when(inventoryRepository.findAll(any(Pageable.class))).thenReturn(inventoryPage);
            when(inventoryMapper.mapToInventoryResponse(inventory)).thenReturn(inventoryResponse);

            PagedResponse<InventoryResponse> result = inventoryService.getAllInventory(0, 10, "location", "asc");

            assertThat(result.getContent()).containsExactly(inventoryResponse);
            assertThat(result.getTotalElements()).isEqualTo(1);
        }

        @Test
        void throwsInvalidSortFieldException_whenSortFieldIsNotAllowed() {
            assertThatThrownBy(() -> inventoryService.getAllInventory(0, 10, "secretInternalField", "asc"))
                    .isInstanceOf(InvalidSortFieldException.class)
                    .hasMessageContaining("secretInternalField");

            verifyNoInteractions(inventoryRepository);
        }

        @Test
        void sortsDescending_whenSortDirIsDesc() {
            when(inventoryRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(inventory)));
            when(inventoryMapper.mapToInventoryResponse(any())).thenReturn(inventoryResponse);

            inventoryService.getAllInventory(0, 10, "quantityAvailable", "desc");

            var pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            verify(inventoryRepository).findAll(pageableCaptor.capture());
            assertThat(pageableCaptor.getValue().getSort().getOrderFor("quantityAvailable").isDescending()).isTrue();
        }
    }

    @Nested
    class UpdateInventory {

        @Test
        void updatesAndReturnsMappedResponse_whenInventoryExists() {
            when(inventoryRepository.findById(1L)).thenReturn(Optional.of(inventory));
            when(inventoryRepository.save(inventory)).thenReturn(inventory);
            when(inventoryMapper.mapToInventoryResponse(inventory)).thenReturn(inventoryResponse);

            InventoryResponse result = inventoryService.updateInventory(1L, inventoryRequest);

            assertThat(result).isEqualTo(inventoryResponse);
            verify(inventoryMapper).mapToInventory(inventoryRequest, inventory);
            verify(inventoryRepository).save(inventory);
        }

        @Test
        void throwsInventoryNotFoundException_whenInventoryDoesNotExist() {
            when(inventoryRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> inventoryService.updateInventory(99L, inventoryRequest))
                    .isInstanceOf(InventoryNotFoundException.class);

            verify(inventoryRepository, never()).save(any());
        }

        @Test
        void allowsUpdate_whenProductAndLocationAreUnchanged() {
            // request matches the existing record's product+location exactly — should NOT
            // trigger the duplicate check against itself
            when(inventoryRepository.findById(1L)).thenReturn(Optional.of(inventory));
            when(inventoryRepository.save(inventory)).thenReturn(inventory);
            when(inventoryMapper.mapToInventoryResponse(inventory)).thenReturn(inventoryResponse);

            inventoryService.updateInventory(1L, inventoryRequest);

            verify(inventoryRepository, never()).existsByProductIdAndLocation(any(), any());
        }

        @Test
        void throwsInventoryAlreadyExistsException_whenChangingToAnotherRecordsProductLocation() {
            when(inventoryRepository.findById(1L)).thenReturn(Optional.of(inventory));

            InventoryRequest changedLocationRequest = new InventoryRequest();
            changedLocationRequest.setProductId(1L);
            changedLocationRequest.setLocation("WH-WEST-02");
            changedLocationRequest.setQuantityAvailable(50);
            changedLocationRequest.setQuantityReserved(5);
            changedLocationRequest.setReorderLevel(10);

            when(inventoryRepository.existsByProductIdAndLocation(1L, "WH-WEST-02")).thenReturn(true);

            assertThatThrownBy(() -> inventoryService.updateInventory(1L, changedLocationRequest))
                    .isInstanceOf(InventoryAlreadyExistsException.class);

            verify(inventoryRepository, never()).save(any());
        }

        @Test
        void throwsProductNotFoundException_whenChangingToAnUnknownProduct() {
            when(inventoryRepository.findById(1L)).thenReturn(Optional.of(inventory));

            InventoryRequest changedProductRequest = new InventoryRequest();
            changedProductRequest.setProductId(99L);
            changedProductRequest.setLocation("WH-EAST-01");
            changedProductRequest.setQuantityAvailable(120);
            changedProductRequest.setQuantityReserved(15);
            changedProductRequest.setReorderLevel(20);

            when(inventoryRepository.existsByProductIdAndLocation(99L, "WH-EAST-01")).thenReturn(false);
            when(productRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> inventoryService.updateInventory(1L, changedProductRequest))
                    .isInstanceOf(ProductNotFoundException.class);

            verify(inventoryRepository, never()).save(any());
        }
    }

    @Nested
    class DeleteInventory {

        @Test
        void deletesInventory_whenItExists() {
            when(inventoryRepository.findById(1L)).thenReturn(Optional.of(inventory));

            inventoryService.deleteInventory(1L);

            verify(inventoryRepository).deleteById(1L);
        }

        @Test
        void throwsInventoryNotFoundException_whenInventoryDoesNotExist_andNeverDeletes() {
            when(inventoryRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> inventoryService.deleteInventory(99L))
                    .isInstanceOf(InventoryNotFoundException.class);

            verify(inventoryRepository, never()).deleteById(anyLong());
        }
    }

    @Nested
    class SearchInventory {

        @Test
        void delegatesToRepositoryAndMapsResults() {
            Page<Inventory> inventoryPage = new PageImpl<>(List.of(inventory));
            Pageable pageable = PageRequest.of(0, 10); // unsorted

            when(inventoryRepository.searchInventory(1L, null, false, pageable))
                    .thenReturn(inventoryPage);
            when(inventoryMapper.mapToInventoryResponse(inventory)).thenReturn(inventoryResponse);

            Page<InventoryResponse> result = inventoryService.searchInventory(1L, null, false, pageable);

            assertThat(result.getContent()).containsExactly(inventoryResponse);
        }

        @Test
        void normalizesSortFieldCaseBeforeDelegatingToRepository() {
            Pageable requestedPageable = PageRequest.of(0, 10, Sort.by("LOCATION").ascending());
            when(inventoryRepository.searchInventory(any(), any(), anyBoolean(), any()))
                    .thenReturn(new PageImpl<>(List.of(inventory)));
            when(inventoryMapper.mapToInventoryResponse(any())).thenReturn(inventoryResponse);

            inventoryService.searchInventory(null, null, false, requestedPageable);

            var pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            verify(inventoryRepository).searchInventory(any(), any(), anyBoolean(), pageableCaptor.capture());
            Sort.Order order = pageableCaptor.getValue().getSort().getOrderFor("location");
            assertThat(order).isNotNull();
            assertThat(order.isAscending()).isTrue();
        }

        @Test
        void throwsInvalidSortFieldException_whenSortFieldNotOnAllowList() {
            Pageable requestedPageable = PageRequest.of(0, 10, Sort.by("bogusField").ascending());

            assertThatThrownBy(() -> inventoryService.searchInventory(null, null, false, requestedPageable))
                    .isInstanceOf(InvalidSortFieldException.class)
                    .hasMessageContaining("bogusField");

            verifyNoInteractions(inventoryRepository);
        }
    }

    @Nested
    class SearchInventoryBySpecification {

        @Test
        void delegatesToRepositoryFindAllWithSpecAndMapsResults() {
            Page<Inventory> inventoryPage = new PageImpl<>(List.of(inventory));
            when(inventoryRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class)))
                    .thenReturn(inventoryPage);
            when(inventoryMapper.mapToInventoryResponse(inventory)).thenReturn(inventoryResponse);

            Page<InventoryResponse> result = inventoryService.searchInventoryBySpecification(
                    1L, "WH-EAST", true, PageRequest.of(0, 10));

            assertThat(result.getContent()).containsExactly(inventoryResponse);
        }
    }
}
