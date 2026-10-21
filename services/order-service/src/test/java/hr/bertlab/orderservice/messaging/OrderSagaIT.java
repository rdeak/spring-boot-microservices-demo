package hr.bertlab.orderservice.messaging;

import static hr.bertlab.saga.messaging.test.SagaTestSupport.counter;
import static hr.bertlab.saga.messaging.test.SagaTestSupport.publish;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import hr.bertlab.orderservice.AbstractOrderServiceIT;
import hr.bertlab.orderservice.model.OrderLine;
import hr.bertlab.orderservice.model.OrderStatus;
import hr.bertlab.orderservice.repository.OrderRepository;
import hr.bertlab.orderservice.service.OrderService;
import hr.bertlab.saga.messaging.events.InventoryReservationFailed;
import hr.bertlab.saga.messaging.events.SagaEventCodec;
import hr.bertlab.saga.messaging.events.ShippingFailed;
import hr.bertlab.saga.messaging.events.ShippingScheduled;
import hr.bertlab.saga.messaging.topology.SagaNames;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

class OrderSagaIT extends AbstractOrderServiceIT {

    @Autowired
    OrderService orders;

    @Autowired
    OrderRepository repository;

    @Autowired
    RabbitTemplate rabbit;

    @Autowired
    SagaNames names;

    @Autowired
    SagaEventCodec codec;

    @Autowired
    MeterRegistry registry;

    @Test
    void shippingScheduledConfirmsTheOrder() {
        UUID id = placeOrder();
        publish(rabbit, names, codec, new ShippingScheduled(id, "DHL", LocalDate.now()));
        awaitStatus(id, OrderStatus.CONFIRMED);
    }

    @Test
    void shippingFailedFailsTheOrderWithReason() {
        UUID id = placeOrder();
        publish(rabbit, names, codec, new ShippingFailed(id, "No carrier available for country 'AQ'"));
        awaitStatus(id, OrderStatus.FAILED);
        assertThat(repository.findById(id).orElseThrow().getFailureReason())
                .isEqualTo("Shipping failed: No carrier available for country 'AQ'");
    }

    @Test
    void inventoryReservationFailedFailsTheOrder() {
        UUID id = placeOrder();
        publish(rabbit, names, codec, new InventoryReservationFailed(id, "Insufficient stock for SKU-LOW"));
        awaitStatus(id, OrderStatus.FAILED);
        assertThat(repository.findById(id).orElseThrow().getFailureReason())
                .startsWith("Inventory reservation failed:");
    }

    @Test
    void lateEventsDoNotChangeATerminalOrder() {
        UUID id = placeOrder();
        publish(rabbit, names, codec, new ShippingFailed(id, "No carrier"));
        awaitStatus(id, OrderStatus.FAILED);
        double processedBefore =
                counter(registry, "saga.events.processed", "eventType", "ShippingScheduled", "outcome", "processed");

        publish(rabbit, names, codec, new ShippingScheduled(id, "DHL", LocalDate.now()));

        await().atMost(Duration.ofSeconds(10))
                .until(() -> counter(
                                registry,
                                "saga.events.processed",
                                "eventType",
                                "ShippingScheduled",
                                "outcome",
                                "processed")
                        > processedBefore);
        assertThat(repository.findById(id).orElseThrow().getStatus()).isEqualTo(OrderStatus.FAILED);
    }

    @Test
    void eventForAnUnknownOrderIsDeadLettered() {
        double before = counter(registry, "saga.alerts", "code", "MESSAGE_DEAD_LETTERED");
        publish(rabbit, names, codec, new ShippingScheduled(UUID.randomUUID(), "DHL", LocalDate.now()));
        await().atMost(Duration.ofSeconds(15))
                .until(() -> counter(registry, "saga.alerts", "code", "MESSAGE_DEAD_LETTERED") > before);
    }

    @Test
    void pendingOrdersOlderThanTheThresholdRaiseOneStuckAlert() {
        double before = counter(registry, "saga.alerts", "severity", "WARN", "code", "SAGA_STUCK");
        UUID id = placeOrder();

        await().atMost(Duration.ofSeconds(10))
                .until(() -> repository.findById(id).orElseThrow().getAlertedStuckAt() != null);
        assertThat(counter(registry, "saga.alerts", "severity", "WARN", "code", "SAGA_STUCK"))
                .isGreaterThan(before);
    }

    @Test
    void stuckAlertIsClaimedOnlyOnce() throws Exception {
        UUID id = placeOrder();
        await().atMost(Duration.ofSeconds(10))
                .until(() -> repository.findById(id).orElseThrow().getAlertedStuckAt() != null);
        Instant claimedAt = repository.findById(id).orElseThrow().getAlertedStuckAt();

        Thread.sleep(1500);
        assertThat(repository.findById(id).orElseThrow().getAlertedStuckAt()).isEqualTo(claimedAt);
    }

    private UUID placeOrder() {
        return orders.place("HR", List.of(new OrderLine("SKU-1", 1))).getId();
    }

    private void awaitStatus(UUID id, OrderStatus expected) {
        await().atMost(Duration.ofSeconds(10))
                .until(() -> repository.findById(id).orElseThrow().getStatus() == expected);
    }
}
