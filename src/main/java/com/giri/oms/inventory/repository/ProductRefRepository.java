package com.giri.oms.inventory.repository;

import com.giri.oms.inventory.entity.ProductRef;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRefRepository extends JpaRepository<ProductRef, Long> {
}
