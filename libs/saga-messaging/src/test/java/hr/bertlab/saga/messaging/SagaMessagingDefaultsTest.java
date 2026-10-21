package hr.bertlab.saga.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

class SagaMessagingDefaultsTest {

    private final SagaMessagingDefaults defaults = new SagaMessagingDefaults();

    @Test
    void shipsTracingDefaults() {
        MockEnvironment environment = new MockEnvironment();

        defaults.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("management.tracing.sampling.probability"))
                .isEqualTo("1.0");
        assertThat(environment.getProperty("management.opentelemetry.tracing.export.otlp.endpoint"))
                .isEqualTo("http://localhost:4318/v1/traces");
        assertThat(environment.getProperty("management.otlp.metrics.export.enabled"))
                .isEqualTo("false");
    }

    @Test
    void applicationPropertiesWin() {
        MockEnvironment environment = new MockEnvironment();
        environment
                .getPropertySources()
                .addFirst(
                        new MapPropertySource("application", Map.of("management.tracing.sampling.probability", "0.2")));

        defaults.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("management.tracing.sampling.probability"))
                .isEqualTo("0.2");
    }

    @Test
    void endpointFollowsTheEnvironmentOverride() {
        MockEnvironment environment = new MockEnvironment();
        environment
                .getPropertySources()
                .addFirst(new MapPropertySource(
                        "env", Map.of("OTLP_TRACES_ENDPOINT", "http://collector:4318/v1/traces")));

        defaults.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("management.opentelemetry.tracing.export.otlp.endpoint"))
                .isEqualTo("http://collector:4318/v1/traces");
    }
}
