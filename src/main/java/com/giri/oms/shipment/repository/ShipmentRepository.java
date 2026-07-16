package com.giri.oms.shipment.repository;

import com.giri.oms.shipment.entity.Shipment;
import com.giri.oms.shipment.entity.ShipmentStatus;
import com.giri.oms.shipment.entity.ShippingCarrier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ShipmentRepository extends JpaRepository<Shipment, Long>, JpaSpecificationExecutor<Shipment> {

    // Derived query methods — Spring parses the method name into SQL.
    // "OrderId" navigates the order relation's id field automatically.

    List<Shipment> findByOrderId(Long orderId);

    List<Shipment> findByStatus(ShipmentStatus status);

    // JPQL @Query — for combining multiple optional filters in one query.
    @Query("""
            SELECT s FROM Shipment s
            WHERE (:orderId IS NULL OR s.order.id = :orderId)
              AND (:status IS NULL OR s.status = :status)
              AND (:carrier IS NULL OR s.carrier = :carrier)
            """)
    Page<Shipment> searchShipments(
            @Param("orderId") Long orderId,
            @Param("status") ShipmentStatus status,
            @Param("carrier") ShippingCarrier carrier,
            Pageable pageable
    );

}
