package hr.bertlab.saga.messaging.test;

import hr.bertlab.saga.messaging.events.EventType;
import hr.bertlab.saga.messaging.events.SagaEvent;
import hr.bertlab.saga.messaging.events.SagaEventCodec;
import hr.bertlab.saga.messaging.events.SagaMessage;
import hr.bertlab.saga.messaging.topology.SagaNames;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * Helpers shared by the library, service and end-to-end tests.
 */
public final class SagaTestSupport {

    /** Matches {@link #schemaFor} names; group 1 is the run id. */
    public static final Pattern TEST_SCHEMA = Pattern.compile("[a-z_]+_t([0-9a-f]{10})");

    /** Matches queue and exchange names under {@link #prefixFor}; group 1 is the run id. */
    public static final Pattern TEST_BROKER_NAME = Pattern.compile("t([0-9a-f]{10})\\..*");

    /** Every saga timer shrunk so tests run fast; {@link #registerIsolation} and the end-to-end test both apply it. */
    public static final Map<String, String> FAST_TIMERS = Map.of(
            "saga.messaging.relay.poll-interval", "100ms",
            "saga.messaging.retry.initial-interval", "20ms",
            "saga.messaging.retry.max-interval", "100ms",
            "saga.messaging.monitor.poll-interval", "200ms");

    private SagaTestSupport() {}

    /**
     * A fresh id per test run. Its schemas and broker prefix all carry it, so earlier runs never leak rows or
     * stale events into this one, and {@link TestLeftoverCleaner} can tell which objects belong together.
     */
    public static String newRunId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    /** The Postgres schema of one service in a test run, e.g. {@code orders_t1a2b3c4d5e}. */
    public static String schemaFor(String base, String runId) {
        return base + "_t" + runId;
    }

    /** The RabbitMQ name prefix of a test run, e.g. {@code t1a2b3c4d5e.}. */
    public static String prefixFor(String runId) {
        return "t" + runId + ".";
    }

    /**
     * Registers a unique schema and topology prefix (both removed again when the context closes), and
     * shrinks every saga timer so tests run fast.
     */
    public static void registerIsolation(DynamicPropertyRegistry registry, String schemaBase) {
        String runId = newRunId();
        String schema = schemaFor(schemaBase, runId);
        String prefix = prefixFor(runId);
        registry.add("app.schema", () -> schema);
        registry.add("saga.messaging.prefix", () -> prefix);
        FAST_TIMERS.forEach((key, value) -> registry.add(key, () -> value));
    }

    /** Event types appended to the outbox for one order, oldest first. */
    public static List<String> outboxEventTypes(JdbcClient jdbc, UUID orderId) {
        return jdbc.sql("select event_type from outbox_event where aggregate_id = :orderId order by id")
                .param("orderId", orderId)
                .query(String.class)
                .list();
    }

    /** Publishes an event exactly as another service's relay would. Returns the generated event id. */
    public static UUID publish(RabbitTemplate rabbit, SagaNames names, SagaEventCodec codec, SagaEvent event) {
        UUID eventId = UUID.randomUUID();
        publish(rabbit, names, codec, event, eventId);
        return eventId;
    }

    /** Publishes with a caller-chosen event id, for example to simulate a redelivered duplicate. */
    public static void publish(
            RabbitTemplate rabbit, SagaNames names, SagaEventCodec codec, SagaEvent event, UUID eventId) {
        EventType type = EventType.of(event);
        Message message = SagaMessage.build(eventId, type, event.orderId(), Instant.now(), codec.encode(event));
        rabbit.send(names.eventsExchange(), type.wireName(), message);
    }

    /** Current value of a counter, or 0 if it has not been registered yet. */
    public static double counter(MeterRegistry registry, String name, String... tags) {
        Counter counter = registry.find(name).tags(tags).counter();
        return counter == null ? 0 : counter.count();
    }

    public static long queueDepth(AmqpAdmin admin, String queue) {
        QueueInformation info = admin.getQueueInfo(queue);
        return info == null ? 0 : info.getMessageCount();
    }

    /**
     * True once every given event id has been recorded in the inbox (committed). Prefer this over
     * exact counter deltas: counters are shared by every test in the module's context.
     */
    public static boolean allProcessed(JdbcClient jdbc, UUID... eventIds) {
        return jdbc.sql("select count(*) from processed_message where event_id in (:ids)")
                        .param("ids", java.util.List.of(eventIds))
                        .query(Long.class)
                        .single()
                == eventIds.length;
    }
}
