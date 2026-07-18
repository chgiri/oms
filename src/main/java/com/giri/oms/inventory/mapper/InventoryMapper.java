package com.giri.oms.inventory.mapper;

import com.giri.oms.inventory.dto.InventoryRequest;
import com.giri.oms.inventory.dto.InventoryResponse;
import com.giri.oms.inventory.entity.Inventory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface InventoryMapper {

    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "productName", source = "product.name")
    InventoryResponse mapToInventoryResponse(Inventory inventory);

    // "product" is intentionally NOT mapped here — resolving a Product entity from
    // a raw productId requires a repository lookup (to validate it exists), which
    // is business logic that belongs in the service layer, not the mapper.
    // "version" (from BaseEntity) is also intentionally NOT mapped — it's an
    // @Version column Hibernate manages itself: on insert it initializes a null
    // version to 0, and leaving it unmapped here on update preserves whatever
    // value was already loaded onto the managed entity, which is exactly what the
    // optimistic-locking check in GlobalExceptionHandler relies on.
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "product", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    Inventory mapToInventory(InventoryRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "product", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    void mapToInventory(InventoryRequest request, @MappingTarget Inventory inventory);
}
