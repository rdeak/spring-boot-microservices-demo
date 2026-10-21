package hr.bertlab.inventoryservice.messaging;

import static hr.bertlab.saga.messaging.test.SagaTestSupport.allProcessed;
import static hr.bertlab.saga.messaging.test.SagaTestSupport.counter;
import static hr.bertlab.saga.messaging.test.SagaTestSupport.outboxEventTypes;
import static hr.bertlab.saga.messaging.test.SagaTestSupport.publish;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hr.bertlab.inventoryservice.AbstractInventoryServiceIT;
import hr.bertlab.saga.messaging.events.OrderCreated;
import hr.bertlab.saga.messaging.events.OrderItem;
import hr.bertlab.saga.messaging.events.SagaEventCodec;
import hr.bertlab.saga.messaging.events.ShippingFailed;
import hr.bertlab.saga.messaging.topology.SagaNames;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

class InventorySagaIT extends AbstractInventoryServiceIT {

    @Autowired
    RabbitTemplate rabbit;

    @Autowired
    SagaNames names;

    @Autowired
    SagaEventCodec codec;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    MeterRegistry registry;

    @Autowired
    MockMvc mvc;

    @Test
    void reservesStockAndPublishesInventoryReserved() {
        String sku = givenStock(10);
        UUID orderId = UUID.randomUUID();

        publish(rabbit, names, codec, orderCreated(orderId, new OrderItem(sku, 3)));

        awaitReservation(orderId, "RESERVED");
        assertStock(sku, 7, 3);
        assertThat(outboxEventTypes(jdbc, orderId)).containsExactly("InventoryReserved");
        assertThat(outboxPayload(orderId)).contains("\"country\": \"HR\"");
    }

    @Test
    void duplicateSkuLinesAreReservedAsTheirSum() {
        String sku = givenStock(10);
        UUID orderId = UUID.randomUUID();

        publish(rabbit, names, codec, orderCreated(orderId, new OrderItem(sku, 2), new OrderItem(sku, 3)));

        awaitReservation(orderId, "RESERVED");
        assertStock(sku, 5, 5);
    }

    @Test
    void insufficientStockRejectsWithoutReservingAnything() {
        String plenty = givenStock(10);
        String scarce = givenStock(1);
        UUID orderId = UUID.randomUUID();

        publish(rabbit, names, codec, orderCreated(orderId, new OrderItem(plenty, 2), new OrderItem(scarce, 2)));

        awaitReservation(orderId, "REJECTED");
        assertStock(plenty, 10, 0);
        assertStock(scarce, 1, 0);
        assertThat(outboxEventTypes(jdbc, orderId)).containsExactly("InventoryReservationFailed");
        assertThat(outboxPayload(orderId)).contains("Insufficient stock for " + scarce);
    }

    @Test
    void unknownSkuRejects() {
        UUID orderId = UUID.randomUUID();
        publish(rabbit, names, codec, orderCreated(orderId, new OrderItem("NO-SUCH-" + UUID.randomUUID(), 1)));
        awaitReservation(orderId, "REJECTED");
        assertThat(outboxPayload(orderId)).contains("Unknown SKU");
    }

    @Test
    void shippingFailedReleasesTheReservation() {
        String sku = givenStock(10);
        UUID orderId = UUID.randomUUID();
        publish(rabbit, names, codec, orderCreated(orderId, new OrderItem(sku, 4)));
        awaitReservation(orderId, "RESERVED");

        publish(rabbit, names, codec, new ShippingFailed(orderId, "No carrier"));

        awaitReservation(orderId, "RELEASED");
        assertStock(sku, 10, 0);
    }

    @Test
    void secondShippingFailedWithADifferentEventIdDoesNotReleaseTwice() {
        String sku = givenStock(10);
        UUID orderId = UUID.randomUUID();
        publish(rabbit, names, codec, orderCreated(orderId, new OrderItem(sku, 4)));
        awaitReservation(orderId, "RESERVED");
        UUID first = publish(rabbit, names, codec, new ShippingFailed(orderId, "No carrier"));
        UUID second = publish(rabbit, names, codec, new ShippingFailed(orderId, "No carrier"));

        await().atMost(Duration.ofSeconds(10)).until(() -> allProcessed(jdbc, first, second));
        assertStock(sku, 10, 0);
    }

    @Test
    void redeliveredOrderCreatedReservesOnce() {
        String sku = givenStock(10);
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        double duplicatesBefore =
                counter(registry, "saga.events.processed", "eventType", "OrderCreated", "outcome", "duplicate");

        publish(rabbit, names, codec, orderCreated(orderId, new OrderItem(sku, 3)), eventId);
        publish(rabbit, names, codec, orderCreated(orderId, new OrderItem(sku, 3)), eventId);

        await().atMost(Duration.ofSeconds(10))
                .until(() ->
                        counter(registry, "saga.events.processed", "eventType", "OrderCreated", "outcome", "duplicate")
                                == duplicatesBefore + 1);
        assertStock(sku, 7, 3);
        assertThat(outboxEventTypes(jdbc, orderId)).containsExactly("InventoryReserved");
    }

    @Test
    void compensationForAnUnknownOrderIsACriticalDeadLetter() {
        double before = counter(registry, "saga.alerts", "severity", "CRITICAL", "code", "COMPENSATION_FAILED");
        publish(rabbit, names, codec, new ShippingFailed(UUID.randomUUID(), "No carrier"));
        await().atMost(Duration.ofSeconds(15))
                .until(() -> counter(registry, "saga.alerts", "severity", "CRITICAL", "code", "COMPENSATION_FAILED")
                        > before);
    }

    @Test
    void stockEndpoint() throws Exception {
        String sku = givenStock(5);
        mvc.perform(get("/api/inventory/{sku}", sku))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(5))
                .andExpect(jsonPath("$.reserved").value(0));
        mvc.perform(get("/api/inventory/{sku}", "NO-SUCH")).andExpect(status().isNotFound());
    }

    private String givenStock(int available) {
        String sku = "T-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.sql("insert into stock (sku, available, reserved, version) values (:sku, :available, 0, 0)")
                .param("sku", sku)
                .param("available", available)
                .update();
        return sku;
    }

    private void awaitReservation(UUID orderId, String status) {
        await().atMost(Duration.ofSeconds(10))
                .until(() -> status.equals(jdbc.sql("select status from reservation where order_id = :id")
                        .param("id", orderId)
                        .query(String.class)
                        .optional()
                        .orElse(null)));
    }

    private void assertStock(String sku, int available, int reserved) {
        assertThat(jdbc.sql("select available, reserved from stock where sku = :sku")
                        .param("sku", sku)
                        .query((rs, i) -> List.of(rs.getInt("available"), rs.getInt("reserved")))
                        .single())
                .containsExactly(available, reserved);
    }

    private String outboxPayload(UUID orderId) {
        return jdbc.sql("select payload::text from outbox_event where aggregate_id = :id order by id desc limit 1")
                .param("id", orderId)
                .query(String.class)
                .single();
    }

    private static OrderCreated orderCreated(UUID orderId, OrderItem... items) {
        return new OrderCreated(orderId, List.of(items), "HR");
    }
}
