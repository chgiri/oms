package com.giri.oms.inventory.mapper;

import com.giri.oms.inventory.dto.InventoryRequest;
import com.giri.oms.inventory.dto.InventoryResponse;
import com.giri.oms.inventory.entity.Inventory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface InventoryMapper {

    // productName is no longer on Inventory (it's a live value owned by the
    // product module, not this module's data) — the service layer resolves it
    // via ProductService and passes it in here explicitly, rather than the
    // mapper reaching across the module boundary itself.
    @Mapping(target = "productName", source = "productName")
    InventoryResponse mapToInventoryResponse(Inventory inventory, String productName);

    // "productId" is intentionally NOT mapped here — resolving/validating a
    // product from a raw productId requires a call to ProductService, which is
    // business logic that belongs in the service layer, not the mapper.
    // "version" (from BaseEntity) is also intentionally NOT mapped — it's an
    // @Version column Hibernate manages itself: on insert it initializes a null
    // version to 0, and leaving it unmapped here on update preserves whatever
    // value was already loaded onto the managed entity, which is exactly what the
    // optimistic-locking check in GlobalExceptionHandler relies on.
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "productId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    Inventory mapToInventory(InventoryRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "productId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    void mapToInventory(InventoryRequest request, @MappingTarget Inventory inventory);
}
