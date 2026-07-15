package com.giri.oms.customer.repository;

import com.giri.oms.common.AbstractIntegrationTest;
import com.giri.oms.customer.entity.Customer;
import com.giri.oms.customer.entity.CustomerStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

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
class CustomerRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private CustomerRepository customerRepository;

    @BeforeEach
    void setUp() {
        customerRepository.deleteAll();

        customerRepository.save(customer("Ada", "Lovelace", "ada.lovelace@example.com", CustomerStatus.ACTIVE));
        customerRepository.save(customer("Grace", "Hopper", "grace.hopper@example.com", CustomerStatus.ACTIVE));
        customerRepository.save(customer("Alan", "Turing", "alan.turing@archive.org", CustomerStatus.INACTIVE));
    }

    private Customer customer(String firstName, String lastName, String email, CustomerStatus status) {
        Customer customer = new Customer();
        customer.setFirstName(firstName);
        customer.setLastName(lastName);
        customer.setEmail(email);
        customer.setStatus(status);
        return customer;
    }

    @Test
    void findByEmailIgnoreCase_matchesRegardlessOfCase() {
        Optional<Customer> result = customerRepository.findByEmailIgnoreCase("ADA.LOVELACE@EXAMPLE.COM");

        assertThat(result).isPresent();
        assertThat(result.get().getFirstName()).isEqualTo("Ada");
    }

    @Test
    void existsByEmailIgnoreCase_trueWhenEmailMatches() {
        assertThat(customerRepository.existsByEmailIgnoreCase("grace.hopper@example.com")).isTrue();
        assertThat(customerRepository.existsByEmailIgnoreCase("nobody@example.com")).isFalse();
    }

    @Test
    void findByStatus_returnsOnlyMatchingStatus() {
        List<Customer> results = customerRepository.findByStatus(CustomerStatus.INACTIVE);

        assertThat(results)
                .extracting(Customer::getLastName)
                .containsExactly("Turing");
    }

    @Test
    void searchCustomers_filtersOnAllProvidedCriteria() {
        Page<Customer> results = customerRepository.searchCustomers(
                "ada", null, CustomerStatus.ACTIVE, PageRequest.of(0, 10));

        assertThat(results.getContent()).hasSize(1);
        assertThat(results.getContent().get(0).getLastName()).isEqualTo("Lovelace");
    }

    @Test
    void searchCustomers_matchesEitherFirstOrLastName() {
        Page<Customer> results = customerRepository.searchCustomers(
                "hopper", null, null, PageRequest.of(0, 10));

        assertThat(results.getContent()).hasSize(1);
        assertThat(results.getContent().get(0).getFirstName()).isEqualTo("Grace");
    }

    @Test
    void searchCustomers_withStatusFilter_excludesOtherStatuses() {
        Page<Customer> results = customerRepository.searchCustomers(
                null, null, CustomerStatus.ACTIVE, PageRequest.of(0, 10));

        assertThat(results.getContent())
                .extracting(Customer::getLastName)
                .doesNotContain("Turing"); // status = INACTIVE
    }

    @Test
    void searchCustomers_withAllNullFilters_returnsEverything() {
        Page<Customer> results = customerRepository.searchCustomers(
                null, null, null, PageRequest.of(0, 10));

        assertThat(results.getTotalElements()).isEqualTo(3);
    }

    @Test
    void fullTextSearch_matchesAgainstNameAndEmail() {
        // Exercises the Postgres-specific to_tsvector/plainto_tsquery native query —
        // this is exactly the kind of test H2 could not validate correctly.
        List<Customer> results = customerRepository.fullTextSearch("Lovelace");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getFirstName()).isEqualTo("Ada");
    }

    @Test
    void fullTextSearch_matchesWordsFromEmailToo() {
        // Requires the repository query to strip punctuation from email before
        // tokenizing — otherwise Postgres treats "alan.turing@archive.org" as one
        // atomic "email" token rather than separate words, and this would never match.
        List<Customer> results = customerRepository.fullTextSearch("archive");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getLastName()).isEqualTo("Turing");
    }
}