package hr.bertlab.saga.messaging.publish;

import java.time.Duration;

public record OutboxStats(long pending, Duration oldestPendingAge) {}
