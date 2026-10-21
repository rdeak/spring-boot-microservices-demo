package hr.bertlab.saga.messaging.consume;

import hr.bertlab.saga.messaging.SagaService;
import hr.bertlab.saga.messaging.events.EventDecodingException;
import hr.bertlab.saga.messaging.events.EventType;
import hr.bertlab.saga.messaging.events.SagaEvent;
import hr.bertlab.saga.messaging.events.SagaEventCodec;
import hr.bertlab.saga.messaging.events.SagaMessage;
import hr.bertlab.saga.messaging.ops.SagaMetrics;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageListener;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.transaction.support.TransactionTemplate;

public class SagaMessageDispatcher implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(SagaMessageDispatcher.class);

    private final SagaHandlers handlers;
    private final Inbox inbox;
    private final SagaEventCodec codec;
    private final TransactionTemplate tx;
    private final SagaMetrics metrics;

    public SagaMessageDispatcher(
            SagaService service,
            SagaHandlers handlers,
            Inbox inbox,
            SagaEventCodec codec,
            TransactionTemplate tx,
            SagaMetrics metrics) {
        if (handlers.service() != service) {
            throw new IllegalStateException(
                    "SagaHandlers bean is for " + handlers.service() + " but saga.messaging.service is " + service);
        }
        Set<EventType> expected = EventType.consumedBy(service);
        EnumSet<EventType> missing = EnumSet.noneOf(EventType.class);
        missing.addAll(expected);
        missing.removeAll(handlers.types());
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    service + " must handle " + expected + " but has no handler for " + missing);
        }
        this.handlers = handlers;
        this.inbox = inbox;
        this.codec = codec;
        this.tx = tx;
        this.metrics = metrics;
    }

    @Override
    public void onMessage(Message message) {
        MessageProperties properties = message.getMessageProperties();
        UUID eventId = uuidHeader(properties, SagaMessage.EVENT_ID);
        UUID orderId = uuidHeader(properties, SagaMessage.ORDER_ID);
        String typeName = stringHeader(properties, SagaMessage.EVENT_TYPE);
        EventType type = EventType.fromWireName(typeName)
                .filter(handlers.types()::contains)
                .orElseThrow(() -> new PoisonMessageException("No handler for event type '" + typeName + "'"));
        SagaEvent event;
        try {
            event = codec.decode(type, message.getBody());
        } catch (EventDecodingException ex) {
            throw new PoisonMessageException(String.valueOf(ex.getMessage()), ex);
        }

        Boolean processed = tx.execute(status -> {
            if (!inbox.tryRecord(eventId, type)) {
                return false;
            }
            handlers.handle(type, event);
            return true;
        });
        boolean handled = Boolean.TRUE.equals(processed);
        metrics.eventProcessed(
                type.wireName(), handled ? SagaMetrics.OUTCOME_PROCESSED : SagaMetrics.OUTCOME_DUPLICATE);
        log.info(
                "Handled {} {} for order {}: {}",
                type.wireName(),
                eventId,
                orderId,
                handled ? "processed" : "duplicate");
    }

    private static String stringHeader(MessageProperties properties, String name) {
        Object value = properties.getHeader(name);
        if (value == null || value.toString().isBlank()) {
            throw new PoisonMessageException("Missing header '" + name + "'");
        }
        return value.toString();
    }

    private static UUID uuidHeader(MessageProperties properties, String name) {
        String value = stringHeader(properties, name);
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new PoisonMessageException("Header '" + name + "' is not a UUID: " + value, ex);
        }
    }
}
