package hr.bertlab.saga.messaging.publish;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import hr.bertlab.saga.messaging.AbstractSagaMessagingIT;
import hr.bertlab.saga.messaging.events.OrderCreated;
import hr.bertlab.saga.messaging.events.OrderItem;
import hr.bertlab.saga.messaging.events.SagaEventCodec;
import hr.bertlab.saga.messaging.events.SagaMessage;
import hr.bertlab.saga.messaging.test.SagaTestSupport;
import hr.bertlab.saga.messaging.topology.SagaNames;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

class OutboxIT extends AbstractSagaMessagingIT {

    private static final String TRACEPARENT = "00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}";

    @Autowired
    Outbox outbox;

    @Autowired
    OutboxRelay relay;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TransactionTemplate sagaTransactionTemplate;

    @Autowired
    RabbitTemplate rabbit;

    @Autowired
    AmqpAdmin admin;

    @Autowired
    CachingConnectionFactory connectionFactory;

    @Autowired
    SagaNames names;

    @Autowired
    Tracer tracer;

    @Autowired
    SagaEventCodec codec;

    private Queue probe;

    @BeforeEach
    void bindProbeQueue() {
        // Sees every published saga event without competing with the service's own consumers.
        // Not auto-delete: rabbit.receive(queue, timeout) consumes and cancels per call, which would delete an
        // auto-delete queue after the first empty poll. Exclusive keeps it connection-scoped as a safety net.
        probe = new Queue("probe." + UUID.randomUUID(), false, true, false);
        admin.declareQueue(probe);
        admin.declareBinding(BindingBuilder.bind(probe)
                .to(new TopicExchange(names.eventsExchange()))
                .with("#"));
    }

    @AfterEach
    void deleteProbeQueue() {
        admin.deleteQueue(probe.getName());
    }

    @Test
    void libraryEnablesConfirmsReturnsAndMandatoryWithoutServiceConfig() {
        assertThat(connectionFactory.isPublisherConfirms()).isTrue();
        assertThat(connectionFactory.isPublisherReturns()).isTrue();
        assertThat(rabbit.isMandatoryFor(new Message(new byte[0]))).isTrue();
    }

    @Test
    void appendOutsideATransactionFailsFast() {
        assertThatThrownBy(() -> outbox.append(orderCreated(UUID.randomUUID(), 1)))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void rolledBackTransactionLeavesNoOutboxRow() {
        UUID orderId = UUID.randomUUID();
        assertThatThrownBy(() -> sagaTransactionTemplate.executeWithoutResult(status -> {
                    outbox.append(orderCreated(orderId, 1));
                    throw new IllegalStateException("business failure after append");
                }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(SagaTestSupport.outboxEventTypes(jdbc, orderId)).isEmpty();
    }

    @Test
    void relayPublishesWithTheTraceCapturedAtAppend() {
        UUID orderId = UUID.randomUUID();
        Span span = tracer.nextSpan().name("place-order").start();
        try (Tracer.SpanInScope scope = tracer.withSpan(span)) {
            sagaTransactionTemplate.executeWithoutResult(status -> outbox.append(orderCreated(orderId, 1)));
        } finally {
            span.end();
        }

        Message published = await().atMost(Duration.ofSeconds(10)).until(() -> receiveFor(orderId), Objects::nonNull);
        String traceparent = published.getMessageProperties().getHeader("traceparent");
        assertThat(traceparent).startsWith("00-" + span.context().traceId() + "-");
        assertThat(traceparent).doesNotContain(span.context().spanId());
    }

    @Test
    void relayPublishesRowsWithoutOrWithBrokenTraceContext() {
        UUID orderId = UUID.randomUUID();
        jdbc.sql("""
                insert into outbox_event (event_id, aggregate_id, event_type, payload, trace_context)
                values (:first, :orderId, 'OrderCreated', cast(:payload as jsonb), null),
                       (:second, :orderId, 'OrderCreated', cast(:payload as jsonb), '{"traceparent": "garbage"}'::jsonb)""")
                .param("first", UUID.randomUUID())
                .param("second", UUID.randomUUID())
                .param("orderId", orderId)
                .param("payload", codec.encode(orderCreated(orderId, 1)))
                .update();

        List<String> traceparents = new ArrayList<>();
        await().atMost(Duration.ofSeconds(10)).until(() -> {
            Message message = receiveFor(orderId);
            if (message != null) {
                traceparents.add(message.getMessageProperties().getHeader("traceparent"));
            }
            return traceparents.size() == 2;
        });
        assertThat(traceparents).allMatch(header -> header != null && header.matches(TRACEPARENT));
    }

    private @Nullable Message receiveFor(UUID orderId) {
        Message message = rabbit.receive(probe.getName(), 200);
        if (message != null
                && orderId.toString().equals(message.getMessageProperties().getHeader(SagaMessage.ORDER_ID))) {
            return message;
        }
        return null;
    }

    @Test
    void appendStoresTheCurrentTraceContext() {
        UUID orderId = UUID.randomUUID();
        Span span = tracer.nextSpan().name("place-order").start();
        try (Tracer.SpanInScope scope = tracer.withSpan(span)) {
            sagaTransactionTemplate.executeWithoutResult(status -> {
                outbox.append(orderCreated(orderId, 1));

                OutboxRow row = pendingRowFor(orderId);
                assertThat(row.traceContext().get("traceparent"))
                        .contains(span.context().traceId());
                status.setRollbackOnly();
            });
        } finally {
            span.end();
        }
    }

    @Test
    void appendOutsideASpanStoresAnEmptyTraceContext() {
        UUID orderId = UUID.randomUUID();
        sagaTransactionTemplate.executeWithoutResult(status -> {
            outbox.append(orderCreated(orderId, 1));

            assertThat(pendingRowFor(orderId).traceContext()).isEmpty();
            status.setRollbackOnly();
        });
    }

    @Test
    void fetchPendingTreatsUnreadableTraceContextAsEmpty() {
        UUID orderId = UUID.randomUUID();
        sagaTransactionTemplate.executeWithoutResult(status -> {
            jdbc.sql("""
                    insert into outbox_event (event_id, aggregate_id, event_type, payload, trace_context)
                    values (:eventId, :orderId, 'OrderCreated', '{}'::jsonb, '"garbage"'::jsonb)""")
                    .param("eventId", UUID.randomUUID())
                    .param("orderId", orderId)
                    .update();

            assertThat(pendingRowFor(orderId).traceContext()).isEmpty();
            status.setRollbackOnly();
        });
    }

    private OutboxRow pendingRowFor(UUID orderId) {
        return outbox.fetchPending(1000).stream()
                .filter(row -> row.aggregateId().equals(orderId))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void relayPublishesInAppendOrderWithSagaHeadersAndMarksRowsPublished() {
        UUID orderId = UUID.randomUUID();
        sagaTransactionTemplate.executeWithoutResult(status -> {
            for (int quantity = 1; quantity <= 5; quantity++) {
                outbox.append(orderCreated(orderId, quantity));
            }
        });

        List<Integer> quantities = new ArrayList<>();
        await().atMost(Duration.ofSeconds(10)).until(() -> {
            Message message = rabbit.receive(probe.getName(), 200);
            if (message != null
                    && orderId.toString().equals(message.getMessageProperties().getHeader(SagaMessage.ORDER_ID))) {
                assertThat((String) message.getMessageProperties().getHeader(SagaMessage.EVENT_TYPE))
                        .isEqualTo("OrderCreated");
                assertThat(message.getMessageProperties().getMessageId())
                        .isEqualTo(message.getMessageProperties().getHeader(SagaMessage.EVENT_ID));
                String body = new String(message.getBody(), StandardCharsets.UTF_8);
                quantities.add(Integer.parseInt(body.replaceAll(".*\"quantity\"\\s*:\\s*(\\d+).*", "$1")));
            }
            return quantities.size() == 5;
        });
        assertThat(quantities).containsExactly(1, 2, 3, 4, 5);

        await().atMost(Duration.ofSeconds(5))
                .until(() ->
                        jdbc.sql("select count(*) from outbox_event where aggregate_id = :id and published_at is null")
                                        .param("id", orderId)
                                        .query(Long.class)
                                        .single()
                                == 0L);
    }

    @Test
    void statsReportPendingCountAndOldestAge() {
        // Inside an uncommitted transaction the relay cannot see the row, so the numbers are stable.
        sagaTransactionTemplate.executeWithoutResult(status -> {
            jdbc.sql("""
                    insert into outbox_event (event_id, aggregate_id, event_type, payload, created_at)
                    values (:eventId, :orderId, 'OrderCreated', '{}'::jsonb, now() - interval '2 minutes')""")
                    .param("eventId", UUID.randomUUID())
                    .param("orderId", UUID.randomUUID())
                    .update();

            OutboxStats stats = outbox.stats();
            assertThat(stats.pending()).isGreaterThanOrEqualTo(1);
            assertThat(stats.oldestPendingAge()).isGreaterThanOrEqualTo(Duration.ofSeconds(119));
            status.setRollbackOnly();
        });
    }

    @Test
    void purgeDeletesOnlyPublishedRowsOlderThanRetention() {
        UUID oldId = UUID.randomUUID();
        UUID freshId = UUID.randomUUID();
        jdbc.sql("""
                insert into outbox_event (event_id, aggregate_id, event_type, payload, published_at)
                values (:old, :orderId, 'OrderCreated', '{}'::jsonb, now() - interval '30 days'),
                       (:fresh, :orderId, 'OrderCreated', '{}'::jsonb, now())""")
                .param("old", oldId)
                .param("fresh", freshId)
                .param("orderId", UUID.randomUUID())
                .update();

        assertThat(relay.purgePublished()).isGreaterThanOrEqualTo(1);

        List<UUID> remaining = jdbc.sql("select event_id from outbox_event where event_id in (:ids)")
                .param("ids", List.of(oldId, freshId))
                .query(UUID.class)
                .list();
        assertThat(remaining).containsExactly(freshId);
    }

    private static OrderCreated orderCreated(UUID orderId, int quantity) {
        return new OrderCreated(orderId, List.of(new OrderItem("SKU-1", quantity)), "HR");
    }
}
