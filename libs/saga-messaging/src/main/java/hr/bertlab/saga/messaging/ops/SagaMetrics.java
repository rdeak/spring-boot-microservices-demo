package hr.bertlab.saga.messaging.ops;

import io.micrometer.core.instrument.MeterRegistry;

public class SagaMetrics {

    public static final String OUTCOME_PROCESSED = "processed";
    public static final String OUTCOME_DUPLICATE = "duplicate";
    public static final String OUTCOME_FAILED = "failed";

    private final MeterRegistry registry;

    public SagaMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void eventProcessed(String eventType, String outcome) {
        registry.counter("saga.events.processed", "eventType", eventType, "outcome", outcome)
                .increment();
    }

    public void deadLettered(String queue, String eventType) {
        registry.counter("saga.dlq.messages", "queue", queue, "eventType", eventType)
                .increment();
    }
}
