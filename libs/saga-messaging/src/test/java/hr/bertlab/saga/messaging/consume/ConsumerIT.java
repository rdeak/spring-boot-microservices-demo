package hr.bertlab.saga.messaging.consume;

import static hr.bertlab.saga.messaging.test.SagaTestSupport.counter;
import static hr.bertlab.saga.messaging.test.SagaTestSupport.publish;
import static hr.bertlab.saga.messaging.test.SagaTestSupport.queueDepth;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import hr.bertlab.saga.messaging.AbstractSagaMessagingIT;
import hr.bertlab.saga.messaging.SagaService;
import hr.bertlab.saga.messaging.events.*;
import hr.bertlab.saga.messaging.publish.Outbox;
import hr.bertlab.saga.messaging.testapp.RecordingHandlers;
import hr.bertlab.saga.messaging.topology.SagaNames;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

class ConsumerIT extends AbstractSagaMessagingIT {

    @Autowired
    RabbitTemplate rabbit;

    @Autowired
    AmqpAdmin admin;

    @Autowired
    SagaNames names;

    @Autowired
    SagaEventCodec codec;

    @Autowired
    RecordingHandlers handlers;

    @Autowired
    MeterRegistry registry;

    @Autowired
    Outbox outbox;

    @Autowired
    TransactionTemplate sagaTransactionTemplate;

    @Test
    void handlesAPublishedEventOnce() {
        UUID orderId = UUID.randomUUID();
        publish(rabbit, names, codec, orderCreated(orderId, 1));

        await().atMost(Duration.ofSeconds(10))
                .until(() -> handlers.receivedFor(orderId).size() == 1);
    }

    @Test
    void handlerRunsOnTheSagaListenerPool() {
        UUID orderId = UUID.randomUUID();
        publish(rabbit, names, codec, orderCreated(orderId, 1));

        await().atMost(Duration.ofSeconds(10))
                .until(() -> handlers.receivedFor(orderId).size() == 1);
        assertThat(handlers.threadFor(orderId)).startsWith("saga-rabbit-");
    }

    @Test
    void duplicateDeliveryOfTheSameEventIdIsSkipped() {
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        double duplicatesBefore =
                counter(registry, "saga.events.processed", "eventType", "OrderCreated", "outcome", "duplicate");

        publish(rabbit, names, codec, orderCreated(orderId, 1), eventId);
        publish(rabbit, names, codec, orderCreated(orderId, 1), eventId);

        await().atMost(Duration.ofSeconds(10))
                .until(() ->
                        counter(registry, "saga.events.processed", "eventType", "OrderCreated", "outcome", "duplicate")
                                == duplicatesBefore + 1);
        assertThat(handlers.receivedFor(orderId)).hasSize(1);
    }

    @Test
    void transientFailureIsRetriedAndProcessedExactlyOnce() {
        UUID orderId = UUID.randomUUID();
        handlers.failTimes(orderId, 2);

        publish(rabbit, names, codec, orderCreated(orderId, 1));

        await().atMost(Duration.ofSeconds(10))
                .until(() -> handlers.receivedFor(orderId).size() == 1);
        // 2 failures + 1 success. If the failed attempt's inbox row were not rolled back,
        // attempt 3 would be treated as a duplicate and never reach the handler.
        assertThat(handlers.attemptsFor(orderId)).isEqualTo(3);
    }

    @Test
    void persistentFailureIsDeadLetteredAfterMaxAttemptsAndAlerted() {
        UUID orderId = UUID.randomUUID();
        handlers.failAlways(orderId);
        String dlq = names.deadLetterQueue(SagaService.INVENTORY);
        long depthBefore = queueDepth(admin, dlq);
        double alertsBefore = counter(registry, "saga.alerts", "severity", "ERROR", "code", "MESSAGE_DEAD_LETTERED");

        publish(rabbit, names, codec, orderCreated(orderId, 1));

        await().atMost(Duration.ofSeconds(15))
                .until(() -> handlers.attemptsFor(orderId) == 5 && queueDepth(admin, dlq) > depthBefore);
        // The alert is raised before the reject, so it is already counted once the DLQ grew.
        assertThat(counter(registry, "saga.alerts", "severity", "ERROR", "code", "MESSAGE_DEAD_LETTERED"))
                .isGreaterThan(alertsBefore);
        handlers.stopFailing(orderId);
    }

    @Test
    void failedCompensationIsACriticalAlert() {
        UUID orderId = UUID.randomUUID();
        handlers.failAlways(orderId);
        double before = counter(registry, "saga.alerts", "severity", "CRITICAL", "code", "COMPENSATION_FAILED");

        publish(rabbit, names, codec, new ShippingFailed(orderId, "No carrier"));

        await().atMost(Duration.ofSeconds(15))
                .until(() -> counter(registry, "saga.alerts", "severity", "CRITICAL", "code", "COMPENSATION_FAILED")
                        > before);
        handlers.stopFailing(orderId);
    }

    @Test
    void undecodableBodyIsPoisonAndDeadLetteredWithoutRetries() {
        UUID orderId = UUID.randomUUID();
        String dlq = names.deadLetterQueue(SagaService.INVENTORY);
        long depthBefore = queueDepth(admin, dlq);
        double alertsBefore = counter(registry, "saga.alerts", "severity", "ERROR", "code", "POISON_MESSAGE");
        double failedBefore =
                counter(registry, "saga.events.processed", "eventType", "OrderCreated", "outcome", "failed");
        double dlqMessagesBefore = deadLetteredOrderCreated();
        Message message =
                SagaMessage.build(UUID.randomUUID(), EventType.ORDER_CREATED, orderId, Instant.now(), "not json");

        long start = System.nanoTime();
        rabbit.send(names.eventsExchange(), "OrderCreated", message);

        await().atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(5))
                .until(() -> queueDepth(admin, dlq) > depthBefore
                        && counter(registry, "saga.alerts", "severity", "ERROR", "code", "POISON_MESSAGE")
                                > alertsBefore);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        // The recoverer ran for this message and the handler never did.
        assertThat(counter(registry, "saga.events.processed", "eventType", "OrderCreated", "outcome", "failed"))
                .isGreaterThanOrEqualTo(failedBefore + 1);
        assertThat(deadLetteredOrderCreated()).isGreaterThanOrEqualTo(dlqMessagesBefore + 1);
        assertThat(handlers.attemptsFor(orderId)).isZero();
        // No retries: five attempts with at least 20+40+80+100 ms of backoff would take 240 ms or more.
        assertThat(elapsed).isLessThan(Duration.ofMillis(150));
    }

    private double deadLetteredOrderCreated() {
        return registry.find("saga.dlq.messages").tag("eventType", "OrderCreated").counters().stream()
                .mapToDouble(Counter::count)
                .sum();
    }

    @Test
    void unknownEventTypeAndMissingEventIdArePoison() {
        double before = counter(registry, "saga.alerts", "severity", "ERROR", "code", "POISON_MESSAGE");
        String orderId = UUID.randomUUID().toString();
        Message unknownType = MessageBuilder.withBody("{}".getBytes(StandardCharsets.UTF_8))
                .setHeader(SagaMessage.EVENT_ID, UUID.randomUUID().toString())
                .setHeader(SagaMessage.EVENT_TYPE, "Bogus")
                .setHeader(SagaMessage.ORDER_ID, orderId)
                .build();
        Message missingEventId = MessageBuilder.withBody("{}".getBytes(StandardCharsets.UTF_8))
                .setHeader(SagaMessage.EVENT_TYPE, "OrderCreated")
                .setHeader(SagaMessage.ORDER_ID, orderId)
                .build();

        // Straight to the service's hash exchange: the topic exchange would not route "Bogus".
        rabbit.send(names.inExchange(SagaService.INVENTORY), "", unknownType);
        rabbit.send(names.inExchange(SagaService.INVENTORY), "", missingEventId);

        await().atMost(Duration.ofSeconds(10))
                .until(() ->
                        counter(registry, "saga.alerts", "severity", "ERROR", "code", "POISON_MESSAGE") >= before + 2);
    }

    @Test
    void eventsOfOneOrderAreHandledInOutboxOrderEndToEnd() {
        UUID orderId = UUID.randomUUID();
        sagaTransactionTemplate.executeWithoutResult(status -> {
            for (int quantity = 1; quantity <= 5; quantity++) {
                outbox.append(orderCreated(orderId, quantity));
            }
        });

        await().atMost(Duration.ofSeconds(10))
                .until(() -> handlers.receivedFor(orderId).size() == 5);
        assertThat(handlers.receivedFor(orderId))
                .extracting(e -> ((OrderCreated) e).items().getFirst().quantity())
                .containsExactly(1, 2, 3, 4, 5);
    }

    private static OrderCreated orderCreated(UUID orderId, int quantity) {
        return new OrderCreated(orderId, List.of(new OrderItem("SKU-1", quantity)), "HR");
    }
}
