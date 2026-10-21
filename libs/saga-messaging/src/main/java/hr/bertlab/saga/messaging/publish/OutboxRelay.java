package hr.bertlab.saga.messaging.publish;

import hr.bertlab.saga.messaging.SagaMessagingProperties;
import hr.bertlab.saga.messaging.events.EventType;
import hr.bertlab.saga.messaging.events.SagaMessage;
import hr.bertlab.saga.messaging.topology.SagaNames;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.support.TransactionTemplate;

public class OutboxRelay {

    static final String PUBLISH_SPAN = "saga.outbox.publish";

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final Outbox outbox;
    private final RabbitTemplate rabbit;
    private final TransactionTemplate tx;
    private final SagaNames names;
    private final OutboxTraceContext tracing;
    private final String lockKey;
    private final SagaMessagingProperties.Relay config;

    public OutboxRelay(
            Outbox outbox,
            RabbitTemplate rabbit,
            TransactionTemplate tx,
            SagaNames names,
            OutboxTraceContext tracing,
            String lockKey,
            SagaMessagingProperties.Relay config) {
        this.outbox = outbox;
        this.rabbit = rabbit;
        this.tx = tx;
        this.names = names;
        this.tracing = tracing;
        this.lockKey = lockKey;
        this.config = config;
    }

    public int publishPending() {
        Integer published = tx.execute(status -> {
            if (!outbox.tryLock(lockKey)) {
                return 0;
            }
            List<OutboxRow> rows = outbox.fetchPending(config.batchSize());
            int count = 0;
            for (OutboxRow row : rows) {
                if (!publish(row)) {
                    break;
                }
                outbox.markPublished(row.id());
                count++;
            }
            return count;
        });
        return published == null ? 0 : published;
    }

    public int purgePublished() {
        Integer purged = tx.execute(status -> outbox.purgePublishedOlderThan(config.retention()));
        return purged == null ? 0 : purged;
    }

    private boolean publish(OutboxRow row) {
        try {
            return send(row);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        } catch (RuntimeException ex) {
            log.error(
                    "Cannot publish outbox row {} (event {}, type {}), stopping batch",
                    row.id(),
                    row.eventId(),
                    row.eventType(),
                    ex);
            return false;
        }
    }

    private boolean send(OutboxRow row) throws InterruptedException {
        EventType type = EventType.fromWireName(row.eventType())
                .orElseThrow(() -> new IllegalStateException(
                        "Outbox row " + row.id() + " has unknown event type " + row.eventType()));
        Message message = SagaMessage.build(row.eventId(), type, row.aggregateId(), row.createdAt(), row.payload());
        CorrelationData correlation = new CorrelationData(row.eventId().toString());
        Span span = tracing.restore(row.traceContext(), PUBLISH_SPAN);
        try (Tracer.SpanInScope scope = tracing.inScope(span)) {
            rabbit.send(names.eventsExchange(), type.wireName(), message, correlation);
            CorrelationData.Confirm confirm =
                    correlation.getFuture().get(config.confirmTimeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!confirm.ack()) {
                log.warn("Broker nacked outbox event {} ({}): {}", row.eventId(), type.wireName(), confirm.reason());
                return false;
            }
            if (correlation.getReturned() != null) {
                log.warn(
                        "Outbox event {} ({}) was unroutable: {}",
                        row.eventId(),
                        type.wireName(),
                        correlation.getReturned().getReplyText());
                return false;
            }
            log.info("Published {} {}", type.wireName(), row.eventId());
            return true;
        } catch (AmqpException | ExecutionException | TimeoutException ex) {
            log.warn(
                    "Publishing outbox event {} ({}) failed, will retry: {}",
                    row.eventId(),
                    type.wireName(),
                    ex.toString());
            return false;
        } finally {
            span.end();
        }
    }
}
