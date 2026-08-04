package com.giri.oms.inventory.repository;

import com.giri.oms.common.AbstractIntegrationTest;
import com.giri.oms.inventory.entity.Inventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @DataJpaTest boots only the JPA slice (repositories, entity manager) — much
 * faster than a full @SpringBootTest, while still running against a real
 * Postgres container so native/Postgres-specific queries are validated for real.
 *
 * @AutoConfigureTestDatabase(replace = NONE) is required — otherwise @DataJpaTest
 * tries to swap in an embedded database, which isn't even on this project's
 * classpath, instead of using the Testcontainers-provided Postgres.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class InventoryRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private InventoryRepository inventoryRepository;

    // Stage 5 of the microservices-prep plan: no real Product rows needed
    // anymore — inventory.product_id has had its FK dropped since Phase 2
    // (V19__drop_cross_module_fk_constraints.sql), and product now lives
    // entirely in product-service's own database, unreachable from this
    // @DataJpaTest slice anyway. Plain ids are enough for every query these
    // tests exercise (all filter/join on productId, none read back a
    // product's own name/price).
    private static final Long mouseId = 1L;
    private static final Long keyboardId = 2L;

    @BeforeEach
    void setUp() {
        inventoryRepository.deleteAll();

        inventoryRepository.save(inventory(mouseId, "WH-EAST-01", 120, 15, 20));   // healthy stock
        inventoryRepository.save(inventory(mouseId, "WH-WEST-02", 5, 0, 20));      // low stock (available <= reorder)
        inventoryRepository.save(inventory(keyboardId, "WH-EAST-01", 40, 5, 10));  // healthy stock
    }

    private Inventory inventory(Long productId, String location, int available, int reserved, int reorderLevel) {
        Inventory inventory = new Inventory();
        inventory.setProductId(productId);
        inventory.setLocation(location);
        inventory.setQuantityAvailable(available);
        inventory.setQuantityReserved(reserved);
        inventory.setReorderLevel(reorderLevel);
        return inventory;
    }

    @Test
    void findByProductIdAndLocation_returnsMatchingRecord() {
        Optional<Inventory> result = inventoryRepository.findByProductIdAndLocation(mouseId, "WH-EAST-01");

        assertThat(result).isPresent();
        assertThat(result.get().getQuantityAvailable()).isEqualTo(120);
    }

    @Test
    void existsByProductIdAndLocation_trueWhenPairExists() {
        assertThat(inventoryRepository.existsByProductIdAndLocation(mouseId, "WH-EAST-01")).isTrue();
        assertThat(inventoryRepository.existsByProductIdAndLocation(mouseId, "WH-NORTH-03")).isFalse();
    }

    @Test
    void findByLocation_returnsAllRecordsAtThatLocation() {
        List<Inventory> results = inventoryRepository.findByLocation("WH-EAST-01");

        assertThat(results).hasSize(2);
    }

    @Test
    void findByProductId_returnsAllLocationsForThatProduct() {
        List<Inventory> results = inventoryRepository.findByProductId(mouseId);

        assertThat(results).hasSize(2);
    }

    @Test
    void uniqueConstraint_rejectsDuplicateProductLocationPair() {
        Inventory duplicate = inventory(mouseId, "WH-EAST-01", 10, 0, 5);

        assertThatThrownBy(() -> inventoryRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void searchInventory_filtersOnAllProvidedCriteria() {
        Page<Inventory> results = inventoryRepository.searchInventory(
                mouseId, "east", false, PageRequest.of(0, 10));

        assertThat(results.getContent()).hasSize(1);
        assertThat(results.getContent().get(0).getLocation()).isEqualTo("WH-EAST-01");
    }

    @Test
    void searchInventory_withLowStockOnly_returnsOnlyRecordsAtOrBelowReorderLevel() {
        Page<Inventory> results = inventoryRepository.searchInventory(
                null, null, true, PageRequest.of(0, 10));

        assertThat(results.getContent()).hasSize(1);
        assertThat(results.getContent().get(0).getLocation()).isEqualTo("WH-WEST-02");
    }

    @Test
    void searchInventory_withAllNullFilters_returnsEverything() {
        Page<Inventory> results = inventoryRepository.searchInventory(
                null, null, false, PageRequest.of(0, 10));

        assertThat(results.getTotalElements()).isEqualTo(3);
    }
}