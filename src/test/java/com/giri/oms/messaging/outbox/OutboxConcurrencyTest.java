package com.giri.oms.messaging.outbox;

import com.giri.oms.common.AbstractIntegrationTest;
import com.giri.oms.messaging.config.KafkaAppProperties;
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

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for the outbox-publisher concurrency gap: running more than
 * one instance of this app previously meant every instance's scheduled poll
 * queried PENDING rows with a plain unlocked SELECT, so two polls landing at
 * the same moment could both fetch, and both publish, the same batch of
 * events — duplicate Kafka sends for every event caught in the overlap.
 *
 * <p>This simulates that scenario directly: two threads call
 * {@link OutboxPublisher#publishPendingEvents()} as close to simultaneously as
 * possible against the same pending batch, standing in for two app instances
 * polling at once. With OutboxEventRepository.findAndLockPendingBatch's
 * {@code FOR UPDATE SKIP LOCKED}, each event is claimed by exactly one of the
 * two — this test fails on the old unlocked query, where every event would be
 * published twice.
 */
@SpringBootTest
class OutboxConcurrencyTest extends AbstractIntegrationTest {

    private static final int EVENT_COUNT = 30;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Autowired
    private KafkaAppProperties kafkaAppProperties;

    @Autowired
    private Clock clock;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        outboxEventRepository.deleteAll();
    }

    @Test
    void concurrentPolls_neverPublishTheSameEventTwice() throws Exception {
        List<String> partitionKeys = IntStream.range(0, EVENT_COUNT)
                .mapToObj(i -> "concurrency-test-" + UUID.randomUUID())
                .toList();

        for (String partitionKey : partitionKeys) {
            OutboxEvent event = OutboxEvent.pending(
                    UUID.randomUUID(),
                    "TestAggregate",
                    partitionKey,
                    "TEST_EVENT",
                    kafkaAppProperties.topics().orderEvents(),
                    partitionKey,
                    "{\"key\":\"" + partitionKey + "\"}",
                    null,
                    clock);
            outboxEventRepository.save(event);
        }

        // batchSize (app.kafka.outbox.batch-size, default 100) comfortably
        // covers all EVENT_COUNT rows in a single poll, so — unprotected —
        // both threads below would each fetch the full pending set and each
        // publish it, i.e. every event sent to Kafka twice.
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger failures = new AtomicInteger();

        Runnable poll = () -> {
            try {
                startLatch.await();
                outboxPublisher.publishPendingEvents();
            } catch (Exception ex) {
                failures.incrementAndGet();
            }
        };

        executor.submit(poll);
        executor.submit(poll);
        startLatch.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        assertThat(failures.get()).isZero();

        // Every event should have been claimed by exactly one of the two
        // pollers and published exactly once — none left PENDING, none
        // double-processed.
        List<OutboxEvent> allEvents = outboxEventRepository.findAll();
        assertThat(allEvents).hasSize(EVENT_COUNT);
        assertThat(allEvents)
                .allSatisfy(event -> assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED));

        // The failure mode this guards against is a duplicate Kafka send, not
        // just a duplicate DB status update — so verify the topic itself:
        // exactly one record per partition key, never two.
        Map<String, Integer> recordCountsByKey = new HashMap<>();
        try (KafkaConsumer<String, String> consumer = createConsumer(UUID.randomUUID().toString())) {
            consumer.subscribe(List.of(kafkaAppProperties.topics().orderEvents()));
            long deadline = System.currentTimeMillis() + 15_000;
            while (System.currentTimeMillis() < deadline
                    && recordCountsByKey.keySet().size() < partitionKeys.size()) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> record : records) {
                    if (partitionKeys.contains(record.key())) {
                        recordCountsByKey.merge(record.key(), 1, Integer::sum);
                    }
                }
            }
        }

        assertThat(recordCountsByKey.keySet()).hasSize(EVENT_COUNT);
        assertThat(recordCountsByKey.values()).allSatisfy(count -> assertThat(count).isEqualTo(1));
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
}
