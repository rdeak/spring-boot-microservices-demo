package hr.bertlab.saga.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import hr.bertlab.saga.messaging.events.EventType;
import hr.bertlab.saga.messaging.events.OrderCreated;
import hr.bertlab.saga.messaging.events.OrderItem;
import hr.bertlab.saga.messaging.events.SagaEventCodec;
import hr.bertlab.saga.messaging.events.SagaMessage;
import hr.bertlab.saga.messaging.publish.Outbox;
import hr.bertlab.saga.messaging.testapp.RecordingHandlers;
import hr.bertlab.saga.messaging.topology.SagaNames;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.support.DefaultMessagePropertiesConverter;
import org.springframework.amqp.rabbit.support.MessagePropertiesConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.transaction.support.TransactionTemplate;

/** One trace from the span that appends an event, through the outbox and the broker, into the handler. */
class TracingIT extends AbstractSagaMessagingIT {

    @Autowired
    Tracer tracer;

    @Autowired
    Outbox outbox;

    @Autowired
    TransactionTemplate sagaTransactionTemplate;

    @Autowired
    RecordingHandlers handlers;

    @Autowired
    RabbitTemplate rabbit;

    @Autowired
    SagaNames names;

    @Autowired
    SagaEventCodec codec;

    @Autowired
    Environment environment;

    @Test
    void importedTracingSettingsBindAndARealTracerIsPresent() {
        // spring.config.import of saga-messaging-tracing.yaml, not Boot's default of 0.1
        assertThat(environment.getProperty("management.tracing.sampling.probability"))
                .isEqualTo("1.0");
        assertThat(tracer).isNotSameAs(Tracer.NOOP);
    }

    @Test
    void handlerRunsInTheTraceThatAppendedTheEvent() {
        UUID orderId = UUID.randomUUID();
        Span span = tracer.nextSpan().name("place-order").start();
        try (Tracer.SpanInScope scope = tracer.withSpan(span)) {
            sagaTransactionTemplate.executeWithoutResult(status -> outbox.append(orderCreated(orderId)));
        } finally {
            span.end();
        }

        await().atMost(Duration.ofSeconds(10))
                .until(() -> handlers.receivedFor(orderId).size() == 1);
        assertThat(handlers.traceIdFor(orderId)).isEqualTo(span.context().traceId());
    }

    @Test
    void handlerContinuesTheTraceOfAReplayedMessage() {
        // DlqReplayer republishes a dead letter with its original properties over the raw channel, so the
        // traceparent header arrives exactly as stored. Simulate that with a hand-built header.
        UUID orderId = UUID.randomUUID();
        String traceId = "4bf92f3577b34da6a3ce929d0e0e4736";
        OrderCreated event = orderCreated(orderId);
        EventType type = EventType.of(event);
        Message message = SagaMessage.build(UUID.randomUUID(), type, orderId, Instant.now(), codec.encode(event));
        message.getMessageProperties().setHeader("traceparent", "00-" + traceId + "-00f067aa0ba902b7-01");
        MessagePropertiesConverter converter = new DefaultMessagePropertiesConverter();
        rabbit.execute(channel -> {
            channel.basicPublish(
                    names.eventsExchange(),
                    type.wireName(),
                    converter.fromMessageProperties(message.getMessageProperties(), StandardCharsets.UTF_8.name()),
                    message.getBody());
            return true;
        });

        await().atMost(Duration.ofSeconds(10))
                .until(() -> handlers.receivedFor(orderId).size() == 1);
        assertThat(handlers.traceIdFor(orderId)).isEqualTo(traceId);
    }

    private static OrderCreated orderCreated(UUID orderId) {
        return new OrderCreated(orderId, List.of(new OrderItem("SKU-1", 1)), "HR");
    }
}
