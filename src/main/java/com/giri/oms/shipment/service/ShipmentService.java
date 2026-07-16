package com.giri.oms.shipment.service;

import com.giri.oms.common.dto.PagedResponse;
import com.giri.oms.shipment.dto.ShipmentRequest;
import com.giri.oms.shipment.dto.ShipmentResponse;
import com.giri.oms.shipment.entity.ShipmentStatus;
import com.giri.oms.shipment.entity.ShippingCarrier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ShipmentService {

    ShipmentResponse createShipment(ShipmentRequest request);

    ShipmentResponse getShipmentById(Long shipmentId);

    PagedResponse<ShipmentResponse> getAllShipments(int pageNo, int pageSize, String sortBy, String sortDir);

    ShipmentResponse updateShipmentStatus(Long shipmentId, ShipmentStatus newStatus, String trackingNumber);

    void deleteShipment(Long shipmentId);

    Page<ShipmentResponse> searchShipments(Long orderId, ShipmentStatus status, ShippingCarrier carrier, Pageable pageable);

    Page<ShipmentResponse> searchShipmentsBySpecification(Long orderId, ShipmentStatus status, ShippingCarrier carrier, Pageable pageable);

}
