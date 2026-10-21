package hr.bertlab.saga.messaging.ops;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class SagaMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final SagaMetrics metrics = new SagaMetrics(registry);

    @Test
    void countsProcessedEventsByTypeAndOutcome() {
        metrics.eventProcessed("OrderCreated", SagaMetrics.OUTCOME_PROCESSED);
        metrics.eventProcessed("OrderCreated", SagaMetrics.OUTCOME_DUPLICATE);
        metrics.eventProcessed("OrderCreated", SagaMetrics.OUTCOME_DUPLICATE);

        assertThat(registry.get("saga.events.processed")
                        .tags("eventType", "OrderCreated", "outcome", "duplicate")
                        .counter()
                        .count())
                .isEqualTo(2.0);
    }

    @Test
    void countsDeadLetteredMessagesByQueueAndType() {
        metrics.deadLettered("inventory.q.0", "ShippingFailed");
        assertThat(registry.get("saga.dlq.messages")
                        .tags("queue", "inventory.q.0", "eventType", "ShippingFailed")
                        .counter()
                        .count())
                .isEqualTo(1.0);
    }
}
