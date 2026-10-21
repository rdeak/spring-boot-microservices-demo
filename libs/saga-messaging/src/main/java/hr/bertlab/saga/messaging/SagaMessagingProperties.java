package hr.bertlab.saga.messaging;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.Assert;

/**
 * Saga messaging configuration, bound from {@code saga.messaging.*}.
 *
 * @param service which saga participant this application is; decides which events it consumes (see
 *     {@code EventType}) and the names of its queues.
 * @param partitions the number of partition queues per service. It MUST be identical in every service,
 *                   because each service declares the complete topology. A mismatch leaves hash buckets
 *                   that no consumer reads. Never decrease the value without first deleting the extra
 *                   {@code <svc>.q.N} queues, because the old queues stay bound to the hash exchange with no consumer.
 */
@ConfigurationProperties("saga.messaging")
public record SagaMessagingProperties(
        SagaService service,
        @DefaultValue("") String prefix,
        @DefaultValue("4") int partitions,
        String adminPath,
        @DefaultValue Relay relay,
        @DefaultValue Retry retry,
        @DefaultValue Monitor monitor) {

    public SagaMessagingProperties {
        Assert.notNull(service, "saga.messaging.service must be set");
        Assert.isTrue(partitions > 0, "saga.messaging.partitions must be positive");
        prefix = prefix == null ? "" : prefix;
    }

    public record Relay(
            @DefaultValue("500ms") Duration pollInterval,
            @DefaultValue("100") int batchSize,
            @DefaultValue("5s") Duration confirmTimeout,
            @DefaultValue("7d") Duration retention) {}

    public record Retry(
            @DefaultValue("5") int maxAttempts,
            @DefaultValue("500ms") Duration initialInterval,
            @DefaultValue("2.0") double multiplier,
            @DefaultValue("8s") Duration maxInterval) {

        public Retry {
            Assert.isTrue(maxAttempts >= 1, "saga.messaging.retry.max-attempts must be at least 1");
        }
    }

    public record Monitor(
            @DefaultValue("30s") Duration pollInterval,
            @DefaultValue("60s") Duration outboxLagThreshold) {}
}
