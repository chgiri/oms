package com.giri.oms.inventory.service.impl;

import com.giri.oms.common.config.CacheConfig;
import com.giri.oms.common.dto.PagedResponse;
import com.giri.oms.common.exception.InvalidSortFieldException;
import com.giri.oms.common.lock.DistributedLockService;
import com.giri.oms.inventory.constants.InventoryConstants;
import com.giri.oms.inventory.dto.InventoryRequest;
import com.giri.oms.inventory.dto.InventoryResponse;
import com.giri.oms.inventory.entity.Inventory;
import com.giri.oms.inventory.exception.InventoryAlreadyExistsException;
import com.giri.oms.inventory.exception.InventoryNotFoundException;
import com.giri.oms.inventory.mapper.InventoryMapper;
import com.giri.oms.inventory.repository.InventoryRepository;
import com.giri.oms.inventory.service.InventoryService;
import com.giri.oms.inventory.specification.InventorySpecification;
import com.giri.oms.product.entity.Product;
import com.giri.oms.product.exception.ProductNotFoundException;
import com.giri.oms.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // class-level default: every method is read-only unless overridden below
public class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;
    private final InventoryMapper inventoryMapper;
    private final DistributedLockService distributedLockService;

    @Value("${app.lock.inventory.wait-seconds}")
    private long lockWaitSeconds;

    @Value("${app.lock.inventory.lease-seconds}")
    private long lockLeaseSeconds;

    private static final String INVENTORY_LOCK_PREFIX = "lock:inventory:";

    private static final Set<String> ALLOWED_SORT_FIELDS =
            Set.of("id", "location", "quantityAvailable", "quantityReserved", "reorderLevel", "createdAt", "updatedAt");

    @Override
    @Transactional // write operation — overrides the class-level readOnly default
    public InventoryResponse createInventory(InventoryRequest request) {
        log.debug("Creating inventory record for product id: {} at location: {}", request.getProductId(), request.getLocation());

        // Same check-then-act shape updateInventory guards against with a lock: two
        // concurrent creates for the same (product, location) pair can both pass
        // existsByProductIdAndLocation before either one saves. The DB's unique
        // constraint (uk_inventory_product_location) still stops the duplicate row, but
        // without this lock the loser would surface as a raw DataIntegrityViolationException
        // instead of the clean 409 a non-racing duplicate request gets. Locking on the
        // (product, location) pair itself — rather than an inventory id, which doesn't
        // exist yet — serializes concurrent creates targeting the same pair.
        return distributedLockService.executeWithLock(
                INVENTORY_LOCK_PREFIX + "create:" + request.getProductId() + ":" + request.getLocation(),
                Duration.ofSeconds(lockWaitSeconds),
                Duration.ofSeconds(lockLeaseSeconds),
                () -> doCreateInventory(request));
    }

    private InventoryResponse doCreateInventory(InventoryRequest request) {
        Product product = getExistingProduct(request.getProductId());

        if (inventoryRepository.existsByProductIdAndLocation(request.getProductId(), request.getLocation())) {
            log.warn("Attempted to create duplicate inventory record for product id: {} at location: {}",
                    request.getProductId(), request.getLocation());
            throw new InventoryAlreadyExistsException(request.getProductId(), request.getLocation());
        }

        Inventory inventory = inventoryMapper.mapToInventory(request);
        inventory.setProduct(product);
        Inventory savedInventory = inventoryRepository.save(inventory);

        log.info(InventoryConstants.INVENTORY_CREATED_LOG, savedInventory.getId());
        return inventoryMapper.mapToInventoryResponse(savedInventory);
    }

    @Override
    @Cacheable(value = CacheConfig.INVENTORY_CACHE, key = "#inventoryId")
    public InventoryResponse getInventoryById(Long inventoryId) {
        log.debug("Fetching inventory record with id: {}", inventoryId);
        return inventoryMapper.mapToInventoryResponse(getExistingInventory(inventoryId));
    }

    @Override
    public PagedResponse<InventoryResponse> getAllInventory(int pageNo, int pageSize, String sortBy, String sortDir) {
        log.debug("Fetching all inventory records");

        validateSortField(sortBy);

        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.DESC.name())
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(pageNo, pageSize, sort);

        Page<Inventory> inventoryPage = inventoryRepository.findAll(pageable);
        Page<InventoryResponse> responsePage = inventoryPage.map(inventoryMapper::mapToInventoryResponse);

        return PagedResponse.of(responsePage);
    }

    private void validateSortField(String sortBy) {
        if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
            throw new InvalidSortFieldException(sortBy, ALLOWED_SORT_FIELDS);
        }
    }

    /**
     * Search endpoints take a raw Pageable straight from request query params (unlike
     * getAllInventory, which validates sortBy up front). A client can send any sort
     * property in any case — e.g. sort=location instead of the exact case Hibernate
     * expects — which, left unchecked, reaches Hibernate as a literal JPQL path and
     * blows up as an UnknownPathException (JPQL attribute paths are case-sensitive).
     * This validates each sort property against the same allow-list and rewrites it
     * to the correct case, so a case-insensitive match still works and anything not
     * on the allow-list gets a clean 400 via InvalidSortFieldException instead of a 500.
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
    @CacheEvict(value = CacheConfig.INVENTORY_CACHE, key = "#inventoryId")
    public InventoryResponse updateInventory(Long inventoryId, InventoryRequest request) {
        log.debug("Updating inventory record with id: {}", inventoryId);

        // Read-modify-write with no optimistic-locking @Version column: two app
        // instances updating the same row at once (e.g. two concurrent stock
        // adjustments) can silently lose one of the writes. A distributed lock
        // keyed by the inventory id serializes that critical section across every
        // instance, not just within this one JVM.
        return distributedLockService.executeWithLock(
                INVENTORY_LOCK_PREFIX + inventoryId,
                Duration.ofSeconds(lockWaitSeconds),
                Duration.ofSeconds(lockLeaseSeconds),
                () -> doUpdateInventory(inventoryId, request));
    }

    private InventoryResponse doUpdateInventory(Long inventoryId, InventoryRequest request) {
        Inventory inventory = getExistingInventory(inventoryId);

        boolean productChanged = !inventory.getProduct().getId().equals(request.getProductId());
        boolean locationChanged = !inventory.getLocation().equals(request.getLocation());

        // Only re-check uniqueness if the (product, location) pair is actually
        // changing — otherwise updating a record with its own unchanged product+location
        // would incorrectly trigger a "duplicate" error against itself.
        if ((productChanged || locationChanged)
                && inventoryRepository.existsByProductIdAndLocation(request.getProductId(), request.getLocation())) {
            log.warn("Attempted to update inventory {} to duplicate product id: {} / location: {}",
                    inventoryId, request.getProductId(), request.getLocation());
            throw new InventoryAlreadyExistsException(request.getProductId(), request.getLocation());
        }

        if (productChanged) {
            inventory.setProduct(getExistingProduct(request.getProductId()));
        }

        inventoryMapper.mapToInventory(request, inventory);
        Inventory updatedInventory = inventoryRepository.save(inventory);

        log.info(InventoryConstants.INVENTORY_UPDATED_LOG, updatedInventory.getId());
        return inventoryMapper.mapToInventoryResponse(updatedInventory);
    }

    @Override
    @Transactional // write operation — overrides the class-level readOnly default
    @CacheEvict(value = CacheConfig.INVENTORY_CACHE, key = "#inventoryId")
    public void deleteInventory(Long inventoryId) {
        log.debug("Deleting inventory record with id: {}", inventoryId);

        getExistingInventory(inventoryId);
        inventoryRepository.deleteById(inventoryId);

        log.info(InventoryConstants.INVENTORY_DELETED_LOG, inventoryId);
    }

    @Override
    public Page<InventoryResponse> searchInventory(Long productId, String location, boolean lowStockOnly, Pageable pageable) {
        Page<Inventory> results = inventoryRepository.searchInventory(productId, location, lowStockOnly, normalizeSort(pageable));
        return results.map(inventoryMapper::mapToInventoryResponse);
    }

    @Override
    public Page<InventoryResponse> searchInventoryBySpecification(Long productId, String location, boolean lowStockOnly, Pageable pageable) {
        var spec = InventorySpecification.buildSearchSpec(productId, location, lowStockOnly);
        Page<Inventory> results = inventoryRepository.findAll(spec, normalizeSort(pageable));
        return results.map(inventoryMapper::mapToInventoryResponse);
    }

    private Inventory getExistingInventory(Long inventoryId) {
        return inventoryRepository.findById(inventoryId)
                .orElseThrow(() -> {
                    log.warn("Inventory record not found with id: {}", inventoryId);
                    return new InventoryNotFoundException(inventoryId);
                });
    }

    private Product getExistingProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> {
                    log.warn("Product not found with id: {} while managing inventory", productId);
                    return new ProductNotFoundException(productId);
                });
    }

}
