package com.giri.oms.inventory.repository;

import com.giri.oms.inventory.entity.InventoryReservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, Long> {

    boolean existsByOrderIdAndProductId(Long orderId, Long productId);

    List<InventoryReservation> findByOrderId(Long orderId);
}
