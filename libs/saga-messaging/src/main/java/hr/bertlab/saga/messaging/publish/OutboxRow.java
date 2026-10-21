package hr.bertlab.saga.messaging.publish;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record OutboxRow(
        long id,
        UUID eventId,
        UUID aggregateId,
        String eventType,
        String payload,
        Instant createdAt,
        Map<String, String> traceContext) {}
