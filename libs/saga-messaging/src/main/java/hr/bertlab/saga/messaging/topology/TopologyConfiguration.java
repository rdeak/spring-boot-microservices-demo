package hr.bertlab.saga.messaging.topology;

import hr.bertlab.saga.messaging.SagaMessagingProperties;
import org.springframework.amqp.core.Declarables;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares every broker object (Spring AMQP's admin creates them on the first connection). Public so
 * the root auto-configuration can import it.
 */
@Configuration(proxyBeanMethods = false)
public class TopologyConfiguration {

    @Bean
    SagaNames sagaNames(SagaMessagingProperties properties) {
        return new SagaNames(properties.prefix());
    }

    @Bean
    Declarables sagaDeclarables(SagaNames names, SagaMessagingProperties properties) {
        return SagaTopology.declarables(names, properties.partitions());
    }
}
