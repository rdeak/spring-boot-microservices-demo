package hr.bertlab.saga.messaging.ops;

import hr.bertlab.saga.messaging.publish.Outbox;
import hr.bertlab.saga.messaging.publish.OutboxStats;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueInformation;

/**
 * Periodically samples DLQ depth and outbox lag into gauges, and alerts when either gets worse.
 */
public class SagaMonitor {

    private static final Logger log = LoggerFactory.getLogger(SagaMonitor.class);

    private final AmqpAdmin admin;
    private final Outbox outbox;
    private final AlertService alerts;
    private final String deadLetterQueue;
    private final Duration outboxLagThreshold;

    private final AtomicLong dlqDepth = new AtomicLong();
    private final AtomicLong outboxPending = new AtomicLong();
    private final AtomicLong outboxOldestAgeSeconds = new AtomicLong();
    private boolean lagAlerted;

    public SagaMonitor(
            AmqpAdmin admin,
            Outbox outbox,
            AlertService alerts,
            String deadLetterQueue,
            Duration outboxLagThreshold,
            MeterRegistry registry) {
        this.admin = admin;
        this.outbox = outbox;
        this.alerts = alerts;
        this.deadLetterQueue = deadLetterQueue;
        this.outboxLagThreshold = outboxLagThreshold;
        Gauge.builder("saga.dlq.depth", dlqDepth, AtomicLong::get)
                .tag("queue", deadLetterQueue)
                .register(registry);
        Gauge.builder("saga.outbox.pending", outboxPending, AtomicLong::get).register(registry);
        Gauge.builder("saga.outbox.oldest.pending.age", outboxOldestAgeSeconds, AtomicLong::get)
                .baseUnit("seconds")
                .register(registry);
    }

    public synchronized void poll() {
        try {
            checkDeadLetterQueue();
        } catch (RuntimeException ex) {
            log.warn("Could not sample dead-letter queue {}: {}", deadLetterQueue, ex.toString());
        }
        try {
            checkOutbox();
        } catch (RuntimeException ex) {
            log.warn("Could not sample outbox: {}", ex.toString());
        }
    }

    private void checkDeadLetterQueue() {
        QueueInformation info = admin.getQueueInfo(deadLetterQueue);
        long depth = info == null ? 0 : info.getMessageCount();
        long previous = dlqDepth.getAndSet(depth);
        if (depth > previous) {
            alerts.raise(
                    AlertCode.DLQ_NOT_EMPTY,
                    Map.of("queue", deadLetterQueue, "depth", depth, "previousDepth", previous));
        }
    }

    private void checkOutbox() {
        OutboxStats stats = outbox.stats();
        outboxPending.set(stats.pending());
        outboxOldestAgeSeconds.set(stats.oldestPendingAge().toSeconds());
        boolean lagging = stats.oldestPendingAge().compareTo(outboxLagThreshold) > 0;
        if (lagging && !lagAlerted) {
            alerts.raise(
                    AlertCode.OUTBOX_LAG,
                    Map.of(
                            "pending", stats.pending(),
                            "oldestPendingAgeSeconds", stats.oldestPendingAge().toSeconds(),
                            "thresholdSeconds", outboxLagThreshold.toSeconds()));
        }
        lagAlerted = lagging;
    }
}
