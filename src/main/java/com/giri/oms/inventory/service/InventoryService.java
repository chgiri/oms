package com.giri.oms.inventory.service;

import com.giri.oms.common.dto.PagedResponse;
import com.giri.oms.inventory.dto.InventoryRequest;
import com.giri.oms.inventory.dto.InventoryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface InventoryService {

    InventoryResponse createInventory(InventoryRequest request);

    InventoryResponse getInventoryById(Long inventoryId);

    PagedResponse<InventoryResponse> getAllInventory(int pageNo, int pageSize, String sortBy, String sortDir);

    InventoryResponse updateInventory(Long inventoryId, InventoryRequest request);

    void deleteInventory(Long inventoryId);

    Page<InventoryResponse> searchInventory(Long productId, String location, boolean lowStockOnly, Pageable pageable);

    Page<InventoryResponse> searchInventoryBySpecification(Long productId, String location, boolean lowStockOnly, Pageable pageable);

}
