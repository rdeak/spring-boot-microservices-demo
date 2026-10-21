package hr.bertlab.saga.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class SagaMessagingPropertiesTest {

    @Test
    void serviceBindsCaseInsensitively() {
        SagaMessagingProperties properties = bind(Map.of("saga.messaging.service", "Inventory"));

        assertThat(properties.service()).isEqualTo(SagaService.INVENTORY);
        assertThat(properties.prefix()).isEmpty();
        assertThat(properties.partitions()).isEqualTo(4);
        assertThat(properties.relay().batchSize()).isEqualTo(100);
        assertThat(properties.retry().maxAttempts()).isEqualTo(5);
    }

    @Test
    void unknownServiceFailsBinding() {
        assertThatThrownBy(() -> bind(Map.of("saga.messaging.service", "billing")))
                .isInstanceOf(BindException.class)
                .hasMessageContaining("saga.messaging");
    }

    private static SagaMessagingProperties bind(Map<String, String> values) {
        return new Binder(new MapConfigurationPropertySource(values))
                .bind("saga.messaging", Bindable.of(SagaMessagingProperties.class))
                .get();
    }
}
