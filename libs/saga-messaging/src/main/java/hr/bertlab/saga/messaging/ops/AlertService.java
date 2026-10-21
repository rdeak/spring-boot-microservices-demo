package hr.bertlab.saga.messaging.ops;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;

/**
 * Single seam for saga alerts: a structured log line plus the {@code saga.alerts} counter that
 * alert rules fire on. Forwarding to a webhook or Alertmanager later only touches this class.
 */
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger("saga.alerts");

    private final MeterRegistry registry;

    public AlertService(MeterRegistry registry) {
        this.registry = registry;
    }

    public void raise(AlertCode code, Map<String, ?> details) {
        Severity severity = code.severity();
        LoggingEventBuilder event = severity == Severity.WARN ? log.atWarn() : log.atError();
        event = event.addKeyValue("alert", code.name()).addKeyValue("severity", severity.name());
        for (Map.Entry<String, ?> detail : details.entrySet()) {
            event = event.addKeyValue(detail.getKey(), detail.getValue());
        }
        event.log("Saga alert {} [{}] {}", code, severity, details);
        registry.counter("saga.alerts", "severity", severity.name(), "code", code.name())
                .increment();
    }
}
