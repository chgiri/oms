package com.giri.oms.shipment.service;

import com.giri.oms.common.dto.PagedResponse;
import com.giri.oms.common.exception.InvalidSortFieldException;
import com.giri.oms.order.entity.Order;
import com.giri.oms.order.exception.OrderNotFoundException;
import com.giri.oms.order.repository.OrderRepository;
import com.giri.oms.shipment.dto.ShipmentResponse;
import com.giri.oms.shipment.dto.ShipmentRequest;
import com.giri.oms.shipment.entity.Shipment;
import com.giri.oms.shipment.entity.ShipmentStatus;
import com.giri.oms.shipment.entity.ShippingCarrier;
import com.giri.oms.shipment.exception.IllegalShipmentStateException;
import com.giri.oms.shipment.exception.ShipmentNotFoundException;
import com.giri.oms.shipment.mapper.ShipmentMapper;
import com.giri.oms.shipment.repository.ShipmentRepository;
import com.giri.oms.shipment.service.impl.ShipmentServiceImpl;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests — no Spring context, no DB. ShipmentRepository, OrderRepository,
 * and mapper are all mocked so these run in milliseconds and only exercise
 * ShipmentServiceImpl's own logic.
 */
@ExtendWith(MockitoExtension.class)
class ShipmentServiceImplTest {

    @Mock
    private ShipmentRepository shipmentRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ShipmentMapper shipmentMapper;

    @InjectMocks
    private ShipmentServiceImpl shipmentService;

    private Order order;
    private Shipment shipment;
    private ShipmentRequest shipmentRequest;
    private ShipmentResponse shipmentResponse;

    @BeforeEach
    void setUp() {
        order = new Order();
        order.setId(1L);

        shipment = new Shipment();
        shipment.setId(1L);
        shipment.setOrder(order);
        shipment.setCarrier(ShippingCarrier.UPS);
        shipment.setStatus(ShipmentStatus.PENDING);
        shipment.setCreatedAt(LocalDateTime.now());
        shipment.setUpdatedAt(LocalDateTime.now());

        shipmentRequest = new ShipmentRequest(1L, ShippingCarrier.UPS);

        shipmentResponse = new ShipmentResponse(
                1L, 1L, ShippingCarrier.UPS, ShipmentStatus.PENDING, null, null, null,
                LocalDateTime.now(), LocalDateTime.now());
    }

    @Nested
    class CreateShipment {

        @Test
        void savesAndReturnsMappedResponse() {
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(shipmentRepository.save(any(Shipment.class))).thenReturn(shipment);
            when(shipmentMapper.mapToShipmentResponse(shipment)).thenReturn(shipmentResponse);

            ShipmentResponse result = shipmentService.createShipment(shipmentRequest);

            assertThat(result.getStatus()).isEqualTo(ShipmentStatus.PENDING);
            assertThat(result.getCarrier()).isEqualTo(ShippingCarrier.UPS);
        }

        @Test
        void throwsOrderNotFoundException_whenOrderDoesNotExist() {
            when(orderRepository.findById(99L)).thenReturn(Optional.empty());
            shipmentRequest.setOrderId(99L);

            assertThatThrownBy(() -> shipmentService.createShipment(shipmentRequest))
                    .isInstanceOf(OrderNotFoundException.class)
                    .hasMessageContaining("99");

            verify(shipmentRepository, never()).save(any());
        }
    }

    @Nested
    class GetShipmentById {

        @Test
        void returnsMappedResponse_whenShipmentExists() {
            when(shipmentRepository.findById(1L)).thenReturn(Optional.of(shipment));
            when(shipmentMapper.mapToShipmentResponse(shipment)).thenReturn(shipmentResponse);

            ShipmentResponse result = shipmentService.getShipmentById(1L);

            assertThat(result).isEqualTo(shipmentResponse);
        }

        @Test
        void throwsShipmentNotFoundException_whenShipmentDoesNotExist() {
            when(shipmentRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> shipmentService.getShipmentById(99L))
                    .isInstanceOf(ShipmentNotFoundException.class)
                    .hasMessageContaining("99");

            verify(shipmentMapper, never()).mapToShipmentResponse(any());
        }
    }

    @Nested
    class GetAllShipments {

        @Test
        void returnsPagedResponse_whenSortFieldIsValid() {
            Page<Shipment> shipmentPage = new PageImpl<>(List.of(shipment));
            when(shipmentRepository.findAll(any(Pageable.class))).thenReturn(shipmentPage);
            when(shipmentMapper.mapToShipmentResponse(shipment)).thenReturn(shipmentResponse);

            PagedResponse<ShipmentResponse> result = shipmentService.getAllShipments(0, 10, "status", "asc");

            assertThat(result.getContent()).containsExactly(shipmentResponse);
            assertThat(result.getTotalElements()).isEqualTo(1);
        }

        @Test
        void throwsInvalidSortFieldException_whenSortFieldIsNotAllowed() {
            assertThatThrownBy(() -> shipmentService.getAllShipments(0, 10, "secretInternalField", "asc"))
                    .isInstanceOf(InvalidSortFieldException.class)
                    .hasMessageContaining("secretInternalField");

            verifyNoInteractions(shipmentRepository);
        }
    }

    @Nested
    class UpdateShipmentStatus {

        @Test
        void transitionsAndReturnsMappedResponse_whenTransitionIsAllowed() {
            when(shipmentRepository.findById(1L)).thenReturn(Optional.of(shipment));
            when(shipmentRepository.save(shipment)).thenReturn(shipment);
            when(shipmentMapper.mapToShipmentResponse(shipment)).thenReturn(shipmentResponse);

            shipmentService.updateShipmentStatus(1L, ShipmentStatus.SHIPPED, "1Z999AA10123456784");

            assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.SHIPPED);
            assertThat(shipment.getTrackingNumber()).isEqualTo("1Z999AA10123456784");
            assertThat(shipment.getShippedAt()).isNotNull();
            verify(shipmentRepository).save(shipment);
        }

        @Test
        void doesNotOverwriteTrackingNumber_whenNoneProvided() {
            shipment.setTrackingNumber("EXISTING123");
            when(shipmentRepository.findById(1L)).thenReturn(Optional.of(shipment));
            when(shipmentRepository.save(shipment)).thenReturn(shipment);
            when(shipmentMapper.mapToShipmentResponse(shipment)).thenReturn(shipmentResponse);

            shipmentService.updateShipmentStatus(1L, ShipmentStatus.SHIPPED, null);

            assertThat(shipment.getTrackingNumber()).isEqualTo("EXISTING123");
        }

        @Test
        void stampsDeliveredAt_whenTransitioningToDelivered() {
            shipment.setStatus(ShipmentStatus.IN_TRANSIT);
            when(shipmentRepository.findById(1L)).thenReturn(Optional.of(shipment));
            when(shipmentRepository.save(shipment)).thenReturn(shipment);
            when(shipmentMapper.mapToShipmentResponse(shipment)).thenReturn(shipmentResponse);

            shipmentService.updateShipmentStatus(1L, ShipmentStatus.DELIVERED, null);

            assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.DELIVERED);
            assertThat(shipment.getDeliveredAt()).isNotNull();
        }

        @Test
        void throwsShipmentNotFoundException_whenShipmentDoesNotExist() {
            when(shipmentRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> shipmentService.updateShipmentStatus(99L, ShipmentStatus.SHIPPED, null))
                    .isInstanceOf(ShipmentNotFoundException.class);

            verify(shipmentRepository, never()).save(any());
        }

        @ParameterizedTest
        @EnumSource(value = ShipmentStatus.class, names = {"IN_TRANSIT", "DELIVERED", "RETURNED"})
        void throwsIllegalShipmentStateException_whenTransitionSkipsAheadOfAllowedNextStatuses(ShipmentStatus illegalTarget) {
            when(shipmentRepository.findById(1L)).thenReturn(Optional.of(shipment)); // shipment starts PENDING

            assertThatThrownBy(() -> shipmentService.updateShipmentStatus(1L, illegalTarget, null))
                    .isInstanceOf(IllegalShipmentStateException.class)
                    .hasMessageContaining("PENDING");

            verify(shipmentRepository, never()).save(any());
        }

        @Test
        void throwsIllegalShipmentStateException_whenTransitioningAwayFromDeliveredTerminalState() {
            shipment.setStatus(ShipmentStatus.DELIVERED);
            when(shipmentRepository.findById(1L)).thenReturn(Optional.of(shipment));

            assertThatThrownBy(() -> shipmentService.updateShipmentStatus(1L, ShipmentStatus.RETURNED, null))
                    .isInstanceOf(IllegalShipmentStateException.class);

            verify(shipmentRepository, never()).save(any());
        }
    }

    @Nested
    class DeleteShipment {

        @Test
        void deletesShipment_whenStatusIsPending() {
            when(shipmentRepository.findById(1L)).thenReturn(Optional.of(shipment)); // PENDING

            shipmentService.deleteShipment(1L);

            verify(shipmentRepository).deleteById(1L);
        }

        @Test
        void deletesShipment_whenStatusIsReturned() {
            shipment.setStatus(ShipmentStatus.RETURNED);
            when(shipmentRepository.findById(1L)).thenReturn(Optional.of(shipment));

            shipmentService.deleteShipment(1L);

            verify(shipmentRepository).deleteById(1L);
        }

        @Test
        void throwsShipmentNotFoundException_whenShipmentDoesNotExist() {
            when(shipmentRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> shipmentService.deleteShipment(99L))
                    .isInstanceOf(ShipmentNotFoundException.class);

            verify(shipmentRepository, never()).deleteById(anyLong());
        }

        @ParameterizedTest
        @EnumSource(value = ShipmentStatus.class, names = {"SHIPPED", "IN_TRANSIT", "DELIVERED"})
        void throwsIllegalShipmentStateException_whenStatusDoesNotAllowDeletion(ShipmentStatus nonDeletableStatus) {
            shipment.setStatus(nonDeletableStatus);
            when(shipmentRepository.findById(1L)).thenReturn(Optional.of(shipment));

            assertThatThrownBy(() -> shipmentService.deleteShipment(1L))
                    .isInstanceOf(IllegalShipmentStateException.class);

            verify(shipmentRepository, never()).deleteById(anyLong());
        }
    }

    @Nested
    class SearchShipments {

        @Test
        void delegatesToRepositoryAndMapsResults() {
            Page<Shipment> shipmentPage = new PageImpl<>(List.of(shipment));
            Pageable pageable = PageRequest.of(0, 10); // unsorted

            when(shipmentRepository.searchShipments(1L, null, null, pageable))
                    .thenReturn(shipmentPage);
            when(shipmentMapper.mapToShipmentResponse(shipment)).thenReturn(shipmentResponse);

            Page<ShipmentResponse> result = shipmentService.searchShipments(1L, null, null, pageable);

            assertThat(result.getContent()).containsExactly(shipmentResponse);
        }

        @Test
        void normalizesSortFieldCaseBeforeDelegatingToRepository() {
            Pageable requestedPageable = PageRequest.of(0, 10, Sort.by("STATUS").ascending());
            when(shipmentRepository.searchShipments(any(), any(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of(shipment)));
            when(shipmentMapper.mapToShipmentResponse(any())).thenReturn(shipmentResponse);

            shipmentService.searchShipments(null, null, null, requestedPageable);

            var pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            verify(shipmentRepository).searchShipments(any(), any(), any(), pageableCaptor.capture());
            Sort.Order sortOrder = pageableCaptor.getValue().getSort().getOrderFor("status");
            assertThat(sortOrder).isNotNull();
            assertThat(sortOrder.isAscending()).isTrue();
        }

        @Test
        void throwsInvalidSortFieldException_whenSortFieldNotOnAllowList() {
            Pageable requestedPageable = PageRequest.of(0, 10, Sort.by("bogusField").ascending());

            assertThatThrownBy(() -> shipmentService.searchShipments(null, null, null, requestedPageable))
                    .isInstanceOf(InvalidSortFieldException.class)
                    .hasMessageContaining("bogusField");

            verifyNoInteractions(shipmentRepository);
        }
    }

    @Nested
    class SearchShipmentsBySpecification {

        @Test
        void delegatesToRepositoryFindAllWithSpecAndMapsResults() {
            Page<Shipment> shipmentPage = new PageImpl<>(List.of(shipment));
            when(shipmentRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class)))
                    .thenReturn(shipmentPage);
            when(shipmentMapper.mapToShipmentResponse(shipment)).thenReturn(shipmentResponse);

            Page<ShipmentResponse> result = shipmentService.searchShipmentsBySpecification(
                    1L, ShipmentStatus.PENDING, ShippingCarrier.UPS, PageRequest.of(0, 10));

            assertThat(result.getContent()).containsExactly(shipmentResponse);
        }
    }
}
