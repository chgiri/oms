package com.giri.oms.messaging.outbox;

import com.giri.oms.common.AbstractIntegrationTest;
import com.giri.oms.customer.entity.Customer;
import com.giri.oms.customer.entity.CustomerStatus;
import com.giri.oms.customer.repository.CustomerRepository;
import com.giri.oms.messaging.config.KafkaAppProperties;
import com.giri.oms.messaging.event.EventType;
import com.giri.oms.messaging.event.OrderCreatedEvent;
import com.giri.oms.order.dto.OrderItemRequest;
import com.giri.oms.order.dto.OrderRequest;
import com.giri.oms.order.dto.OrderResponse;
import com.giri.oms.order.repository.OrderRepository;
import com.giri.oms.order.service.OrderService;
import com.giri.oms.product.entity.Product;
import com.giri.oms.product.repository.ProductRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OrderCreatedOutboxIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Autowired
    private KafkaAppProperties kafkaAppProperties;

    @Autowired
    private JsonMapper objectMapper;

    @BeforeEach
    void setUp() {
        cleanUp();
    }

    // This class is @SpringBootTest, not @DataJpaTest, so it does not get an
    // automatic transactional rollback after each test — data committed by
    // createOrder() is real and persists in the shared Postgres container
    // (see AbstractIntegrationTest) beyond this test class's own run. Without
    // this, the Product/Order/OrderItem rows created here leak into whichever
    // test class Maven happens to run next, causing FK-constraint failures
    // there (e.g. ProductRepositoryTest's deleteAll() in @BeforeEach).
    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        outboxEventRepository.deleteAll();
        orderRepository.deleteAll();
        customerRepository.deleteAll();
        productRepository.deleteAll();
    }

    @Test
    void createOrder_persistsOutboxEvent_andPublisherSendsOrderCreatedToKafka() throws Exception {
        Customer customer = customerRepository.save(customer("Grace", "Hopper", "grace@example.com"));
        Product product = productRepository.save(product("Mechanical Keyboard", "89.99"));

        OrderRequest request = new OrderRequest(customer.getId(), List.of(new OrderItemRequest(product.getId(), 2)));

        OrderResponse createdOrder = orderService.createOrder(request);

        List<OutboxEvent> pendingEvents = outboxEventRepository.findByStatusOrderByCreatedAtAsc(
                OutboxEventStatus.PENDING,
                org.springframework.data.domain.PageRequest.of(0, 10));

        assertThat(pendingEvents).hasSize(1);
        OutboxEvent outboxEvent = pendingEvents.get(0);
        assertThat(outboxEvent.getEventType()).isEqualTo(EventType.ORDER_CREATED);
        assertThat(outboxEvent.getAggregateType()).isEqualTo("Order");
        assertThat(outboxEvent.getAggregateId()).isEqualTo(createdOrder.getId().toString());
        assertThat(outboxEvent.getTopic()).isEqualTo(kafkaAppProperties.topics().orderEvents());
        assertThat(outboxEvent.getPartitionKey()).isEqualTo(createdOrder.getId().toString());

        OrderCreatedEvent payload = objectMapper.readValue(outboxEvent.getPayload(), OrderCreatedEvent.class);
        assertThat(payload.orderId()).isEqualTo(createdOrder.getId());
        assertThat(payload.customerId()).isEqualTo(customer.getId());
        assertThat(payload.items()).hasSize(1);
        assertThat(payload.items().get(0).productId()).isEqualTo(product.getId());

        outboxPublisher.publishPendingEvents();

        OutboxEvent publishedEvent = outboxEventRepository.findById(outboxEvent.getId()).orElseThrow();
        assertThat(publishedEvent.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(publishedEvent.getPublishedAt()).isNotNull();

        try (KafkaConsumer<String, String> consumer = createConsumer(UUID.randomUUID().toString())) {
            consumer.subscribe(List.of(kafkaAppProperties.topics().orderEvents()));

            ConsumerRecord<String, String> record = pollForRecord(consumer, createdOrder.getId().toString());
            assertThat(record.key()).isEqualTo(createdOrder.getId().toString());

            OrderCreatedEvent kafkaPayload = objectMapper.readValue(record.value(), OrderCreatedEvent.class);
            assertThat(kafkaPayload.eventId()).isEqualTo(outboxEvent.getId());
            assertThat(kafkaPayload.orderId()).isEqualTo(createdOrder.getId());
        }
    }

    private KafkaConsumer<String, String> createConsumer(String groupId) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new KafkaConsumer<>(props);
    }

    private ConsumerRecord<String, String> pollForRecord(KafkaConsumer<String, String> consumer, String expectedKey) {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, String> record : records) {
                if (expectedKey.equals(record.key())) {
                    return record;
                }
            }
        }
        throw new AssertionError("No Kafka record received for key " + expectedKey);
    }

    private Customer customer(String firstName, String lastName, String email) {
        Customer customer = new Customer();
        customer.setFirstName(firstName);
        customer.setLastName(lastName);
        customer.setEmail(email);
        customer.setStatus(CustomerStatus.ACTIVE);
        return customer;
    }

    private Product product(String name, String price) {
        Product product = new Product();
        product.setName(name);
        product.setPrice(new BigDecimal(price));
        return product;
    }
}