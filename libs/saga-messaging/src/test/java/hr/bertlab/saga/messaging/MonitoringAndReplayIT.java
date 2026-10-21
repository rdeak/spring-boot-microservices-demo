package hr.bertlab.saga.messaging;

import static hr.bertlab.saga.messaging.test.SagaTestSupport.counter;
import static hr.bertlab.saga.messaging.test.SagaTestSupport.publish;
import static hr.bertlab.saga.messaging.test.SagaTestSupport.queueDepth;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hr.bertlab.saga.messaging.events.OrderCreated;
import hr.bertlab.saga.messaging.events.OrderItem;
import hr.bertlab.saga.messaging.events.SagaEventCodec;
import hr.bertlab.saga.messaging.ops.SagaMonitor;
import hr.bertlab.saga.messaging.testapp.RecordingHandlers;
import hr.bertlab.saga.messaging.topology.SagaNames;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

class MonitoringAndReplayIT extends AbstractSagaMessagingIT {

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
    SagaMonitor monitor;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TransactionTemplate sagaTransactionTemplate;

    @Autowired
    WebApplicationContext context;

    @Test
    void deadLetterRaisesDlqDepthGaugeAndDlqNotEmptyAlert() {
        UUID orderId = UUID.randomUUID();
        handlers.failAlways(orderId);
        double alertsBefore = counter(registry, "saga.alerts", "code", "DLQ_NOT_EMPTY");
        String dlq = names.deadLetterQueue(SagaService.INVENTORY);
        long depthBefore = queueDepth(admin, dlq);

        publish(rabbit, names, codec, orderCreated(orderId));

        await().atMost(Duration.ofSeconds(15))
                .until(() -> handlers.attemptsFor(orderId) >= 5 && queueDepth(admin, dlq) > depthBefore);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(registry.get("saga.dlq.depth").tag("queue", dlq).gauge().value())
                    .isGreaterThan(0);
            assertThat(counter(registry, "saga.alerts", "code", "DLQ_NOT_EMPTY"))
                    .isGreaterThan(alertsBefore);
        });
        handlers.stopFailing(orderId);
    }

    @Test
    void outboxLagAboveThresholdRaisesOutboxLagAlert() {
        double before = counter(registry, "saga.alerts", "severity", "ERROR", "code", "OUTBOX_LAG");
        sagaTransactionTemplate.executeWithoutResult(status -> {
            jdbc.sql("""
                    insert into outbox_event (event_id, aggregate_id, event_type, payload, created_at)
                    values (:eventId, :orderId, 'OrderCreated', '{}'::jsonb, now() - interval '10 minutes')""")
                    .param("eventId", UUID.randomUUID())
                    .param("orderId", UUID.randomUUID())
                    .update();
            monitor.poll(); // same thread, same transaction: sees the uncommitted lagging row
            status.setRollbackOnly();
        });
        assertThat(counter(registry, "saga.alerts", "severity", "ERROR", "code", "OUTBOX_LAG"))
                .isGreaterThan(before);
    }

    @Test
    void replayMovesDeadLettersBackAndTheyAreProcessedOnce() throws Exception {
        UUID orderId = UUID.randomUUID();
        handlers.failAlways(orderId);
        String dlq = names.deadLetterQueue(SagaService.INVENTORY);
        long depthBefore = queueDepth(admin, dlq);
        publish(rabbit, names, codec, orderCreated(orderId));
        await().atMost(Duration.ofSeconds(15))
                .until(() -> handlers.attemptsFor(orderId) >= 5 && queueDepth(admin, dlq) > depthBefore);

        handlers.stopFailing(orderId);
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
        mvc.perform(post("/admin/dlq/replay").param("max", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replayed").isNumber());

        await().atMost(Duration.ofSeconds(10))
                .until(() -> handlers.receivedFor(orderId).size() == 1);
    }

    @Test
    void replayRejectsAnOutOfRangeMax() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
        mvc.perform(post("/admin/dlq/replay").param("max", "0")).andExpect(status().isBadRequest());
    }

    private static OrderCreated orderCreated(UUID orderId) {
        return new OrderCreated(orderId, List.of(new OrderItem("SKU-1", 1)), "HR");
    }
}
