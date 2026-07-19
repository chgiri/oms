package com.giri.oms.shipment.service.impl;

import com.giri.oms.common.dto.PagedResponse;
import com.giri.oms.common.exception.InvalidSortFieldException;
import com.giri.oms.order.entity.Order;
import com.giri.oms.order.exception.OrderNotFoundException;
import com.giri.oms.order.repository.OrderRepository;
import com.giri.oms.shipment.constants.ShipmentConstants;
import com.giri.oms.shipment.dto.ShipmentRequest;
import com.giri.oms.shipment.dto.ShipmentResponse;
import com.giri.oms.shipment.entity.Shipment;
import com.giri.oms.shipment.entity.ShipmentStatus;
import com.giri.oms.shipment.entity.ShippingCarrier;
import com.giri.oms.shipment.exception.IllegalShipmentStateException;
import com.giri.oms.shipment.exception.ShipmentNotFoundException;
import com.giri.oms.shipment.mapper.ShipmentMapper;
import com.giri.oms.shipment.repository.ShipmentRepository;
import com.giri.oms.shipment.service.ShipmentService;
import com.giri.oms.shipment.specification.ShipmentSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // class-level default: every method is read-only unless overridden below
public class ShipmentServiceImpl implements ShipmentService {

    private final ShipmentRepository shipmentRepository;
    private final OrderRepository orderRepository;
    private final ShipmentMapper shipmentMapper;
    private final Clock clock;

    private static final Set<String> ALLOWED_SORT_FIELDS =
            Set.of("id", "status", "carrier", "shippedAt", "deliveredAt", "createdAt", "updatedAt");

    // Defines which statuses a shipment may move to from its current one. Any pair not
    // listed here (including staying in place) is rejected as an illegal transition.
    // DELIVERED and RETURNED are terminal — absent as keys, so any move from them fails.
    private static final Map<ShipmentStatus, Set<ShipmentStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(ShipmentStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(ShipmentStatus.PENDING, EnumSet.of(ShipmentStatus.SHIPPED));
        ALLOWED_TRANSITIONS.put(ShipmentStatus.SHIPPED, EnumSet.of(ShipmentStatus.IN_TRANSIT, ShipmentStatus.RETURNED));
        ALLOWED_TRANSITIONS.put(ShipmentStatus.IN_TRANSIT, EnumSet.of(ShipmentStatus.DELIVERED, ShipmentStatus.RETURNED));
    }

    // Shipments can only be deleted before they've actually shipped, or once a
    // shipment has been returned — once a shipment is in transit or delivered,
    // deleting the record would silently lose the audit trail.
    private static final Set<ShipmentStatus> DELETABLE_STATUSES = EnumSet.of(ShipmentStatus.PENDING, ShipmentStatus.RETURNED);

    @Override
    @Transactional // write operation — overrides the class-level readOnly default
    public ShipmentResponse createShipment(ShipmentRequest request) {
        log.debug("Creating shipment for order id: {}", request.getOrderId());

        Order order = getExistingOrder(request.getOrderId());

        Shipment shipment = new Shipment();
        shipment.setOrder(order);
        shipment.setCarrier(request.getCarrier());
        shipment.setStatus(ShipmentStatus.PENDING);

        Shipment savedShipment = shipmentRepository.save(shipment);

        log.info(ShipmentConstants.SHIPMENT_CREATED_LOG, savedShipment.getId());
        return shipmentMapper.mapToShipmentResponse(savedShipment);
    }

    @Override
    public ShipmentResponse getShipmentById(Long shipmentId) {
        log.debug("Fetching shipment with id: {}", shipmentId);
        return shipmentMapper.mapToShipmentResponse(getExistingShipment(shipmentId));
    }

    @Override
    public PagedResponse<ShipmentResponse> getAllShipments(int pageNo, int pageSize, String sortBy, String sortDir) {
        log.debug("Fetching all shipments");

        validateSortField(sortBy);

        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.DESC.name())
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(pageNo, pageSize, sort);

        Page<Shipment> shipmentPage = shipmentRepository.findAll(pageable);
        Page<ShipmentResponse> responsePage = shipmentPage.map(shipmentMapper::mapToShipmentResponse);

        return PagedResponse.of(responsePage);
    }

    private void validateSortField(String sortBy) {
        if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
            throw new InvalidSortFieldException(sortBy, ALLOWED_SORT_FIELDS);
        }
    }

    /**
     * Search endpoints take a raw Pageable straight from request query params (unlike
     * getAllShipments, which validates sortBy up front). A client can send any sort
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
    public ShipmentResponse updateShipmentStatus(Long shipmentId, ShipmentStatus newStatus, String trackingNumber) {
        log.debug("Updating shipment id: {} status to: {}", shipmentId, newStatus);

        Shipment shipment = getExistingShipment(shipmentId);
        ShipmentStatus currentStatus = shipment.getStatus();

        Set<ShipmentStatus> allowedNextStatuses = ALLOWED_TRANSITIONS.getOrDefault(currentStatus, Set.of());
        if (!allowedNextStatuses.contains(newStatus)) {
            log.warn("Rejected illegal status transition for shipment id: {} — {} -> {}", shipmentId, currentStatus, newStatus);
            throw new IllegalShipmentStateException(
                    String.format(ShipmentConstants.INVALID_STATUS_TRANSITION_MESSAGE, shipmentId, currentStatus, newStatus));
        }

        shipment.setStatus(newStatus);
        if (trackingNumber != null) {
            shipment.setTrackingNumber(trackingNumber);
        }
        if (newStatus == ShipmentStatus.SHIPPED && shipment.getShippedAt() == null) {
            shipment.setShippedAt(LocalDateTime.now(clock));
        }
        if (newStatus == ShipmentStatus.DELIVERED) {
            shipment.setDeliveredAt(LocalDateTime.now(clock));
        }

        Shipment updatedShipment = shipmentRepository.save(shipment);

        log.info(ShipmentConstants.SHIPMENT_STATUS_UPDATED_LOG, updatedShipment.getId(), newStatus);
        return shipmentMapper.mapToShipmentResponse(updatedShipment);
    }

    @Override
    @Transactional // write operation — overrides the class-level readOnly default
    public void deleteShipment(Long shipmentId) {
        log.debug("Deleting shipment with id: {}", shipmentId);

        Shipment shipment = getExistingShipment(shipmentId);
        if (!DELETABLE_STATUSES.contains(shipment.getStatus())) {
            log.warn("Rejected delete of shipment id: {} in non-deletable status: {}", shipmentId, shipment.getStatus());
            throw new IllegalShipmentStateException(
                    String.format(ShipmentConstants.SHIPMENT_NOT_DELETABLE_MESSAGE, shipmentId, shipment.getStatus()));
        }

        shipmentRepository.deleteById(shipmentId);

        log.info(ShipmentConstants.SHIPMENT_DELETED_LOG, shipmentId);
    }

    @Override
    public Page<ShipmentResponse> searchShipments(Long orderId, ShipmentStatus status, ShippingCarrier carrier, Pageable pageable) {
        Page<Shipment> results = shipmentRepository.searchShipments(orderId, status, carrier, normalizeSort(pageable));
        return results.map(shipmentMapper::mapToShipmentResponse);
    }

    @Override
    public Page<ShipmentResponse> searchShipmentsBySpecification(Long orderId, ShipmentStatus status, ShippingCarrier carrier, Pageable pageable) {
        var spec = ShipmentSpecification.buildSearchSpec(orderId, status, carrier);
        Page<Shipment> results = shipmentRepository.findAll(spec, normalizeSort(pageable));
        return results.map(shipmentMapper::mapToShipmentResponse);
    }

    private Shipment getExistingShipment(Long shipmentId) {
        return shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> {
                    log.warn("Shipment not found with id: {}", shipmentId);
                    return new ShipmentNotFoundException(shipmentId);
                });
    }

    private Order getExistingOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> {
                    log.warn("Order not found with id: {} while creating shipment", orderId);
                    return new OrderNotFoundException(orderId);
                });
    }

}
