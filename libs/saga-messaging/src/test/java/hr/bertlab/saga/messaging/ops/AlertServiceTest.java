package hr.bertlab.saga.messaging.ops;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AlertServiceTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final AlertService alerts = new AlertService(registry);

    @Test
    void countsAlertsBySeverityAndCode() {
        alerts.raise(AlertCode.COMPENSATION_FAILED, Map.of("orderId", "42"));
        alerts.raise(AlertCode.COMPENSATION_FAILED, Map.of());
        alerts.raise(AlertCode.SAGA_STUCK, Map.of());

        assertThat(registry.get("saga.alerts")
                        .tags("severity", "CRITICAL", "code", "COMPENSATION_FAILED")
                        .counter()
                        .count())
                .isEqualTo(2.0);
        assertThat(registry.get("saga.alerts")
                        .tags("severity", "WARN", "code", "SAGA_STUCK")
                        .counter()
                        .count())
                .isEqualTo(1.0);
    }

    @Test
    void acceptsNullDetailValues() {
        java.util.HashMap<String, Object> details = new java.util.HashMap<>();
        details.put("orderId", null);
        alerts.raise(AlertCode.POISON_MESSAGE, details);
        assertThat(registry.get("saga.alerts")
                        .tags("code", "POISON_MESSAGE")
                        .counter()
                        .count())
                .isEqualTo(1.0);
    }
}
