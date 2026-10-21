package hr.bertlab.saga.messaging.events;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;

/**
 * The wire format of a saga event: the JSON payload as body plus four string headers. The relay
 * builds messages with {@link #build}; the dispatcher and the dead-letter recoverer read the headers.
 * The event type travels in a header, never in the body.
 */
public final class SagaMessage {

    /** Deduplication key. Also set as the AMQP message id. */
    public static final String EVENT_ID = "eventId";

    /** {@link EventType#wireName()}, used to pick the handler. */
    public static final String EVENT_TYPE = "eventType";

    /** Partition key. Hashed by the consistent-hash exchanges, so it must always be a string. */
    public static final String ORDER_ID = "orderId";

    /** When the event was appended to the outbox, as an ISO-8601 instant. Diagnostics only. */
    public static final String OCCURRED_AT = "occurredAt";

    private SagaMessage() {}

    public static Message build(UUID eventId, EventType type, UUID orderId, Instant occurredAt, String payloadJson) {
        return MessageBuilder.withBody(payloadJson.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setMessageId(eventId.toString())
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setHeader(EVENT_ID, eventId.toString())
                .setHeader(EVENT_TYPE, type.wireName())
                .setHeader(ORDER_ID, orderId.toString())
                .setHeader(OCCURRED_AT, occurredAt.toString())
                .build();
    }
}
