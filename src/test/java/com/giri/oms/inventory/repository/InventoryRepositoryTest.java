package com.giri.oms.inventory.repository;

import com.giri.oms.common.AbstractIntegrationTest;
import com.giri.oms.inventory.entity.Inventory;
import com.giri.oms.product.entity.Product;
import com.giri.oms.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
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

    @Autowired
    private ProductRepository productRepository;

    private Product mouse;
    private Product keyboard;

    @BeforeEach
    void setUp() {
        inventoryRepository.deleteAll();
        productRepository.deleteAll();

        mouse = productRepository.save(product("Wireless Mouse", "25.99", 200));
        keyboard = productRepository.save(product("Mechanical Keyboard", "89.99", 40));

        inventoryRepository.save(inventory(mouse, "WH-EAST-01", 120, 15, 20));   // healthy stock
        inventoryRepository.save(inventory(mouse, "WH-WEST-02", 5, 0, 20));      // low stock (available <= reorder)
        inventoryRepository.save(inventory(keyboard, "WH-EAST-01", 40, 5, 10));  // healthy stock
    }

    private Product product(String name, String price, int stock) {
        Product product = new Product();
        product.setName(name);
        product.setPrice(new BigDecimal(price));
        product.setStock(stock);
        return product;
    }

    private Inventory inventory(Product product, String location, int available, int reserved, int reorderLevel) {
        Inventory inventory = new Inventory();
        inventory.setProduct(product);
        inventory.setLocation(location);
        inventory.setQuantityAvailable(available);
        inventory.setQuantityReserved(reserved);
        inventory.setReorderLevel(reorderLevel);
        return inventory;
    }

    @Test
    void findByProductIdAndLocation_returnsMatchingRecord() {
        Optional<Inventory> result = inventoryRepository.findByProductIdAndLocation(mouse.getId(), "WH-EAST-01");

        assertThat(result).isPresent();
        assertThat(result.get().getQuantityAvailable()).isEqualTo(120);
    }

    @Test
    void existsByProductIdAndLocation_trueWhenPairExists() {
        assertThat(inventoryRepository.existsByProductIdAndLocation(mouse.getId(), "WH-EAST-01")).isTrue();
        assertThat(inventoryRepository.existsByProductIdAndLocation(mouse.getId(), "WH-NORTH-03")).isFalse();
    }

    @Test
    void findByLocation_returnsAllRecordsAtThatLocation() {
        List<Inventory> results = inventoryRepository.findByLocation("WH-EAST-01");

        assertThat(results).hasSize(2);
    }

    @Test
    void findByProductId_returnsAllLocationsForThatProduct() {
        List<Inventory> results = inventoryRepository.findByProductId(mouse.getId());

        assertThat(results).hasSize(2);
    }

    @Test
    void uniqueConstraint_rejectsDuplicateProductLocationPair() {
        Inventory duplicate = inventory(mouse, "WH-EAST-01", 10, 0, 5);

        assertThatThrownBy(() -> inventoryRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void searchInventory_filtersOnAllProvidedCriteria() {
        Page<Inventory> results = inventoryRepository.searchInventory(
                mouse.getId(), "east", false, PageRequest.of(0, 10));

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
