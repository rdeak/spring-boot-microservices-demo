package hr.bertlab.saga.messaging;

import hr.bertlab.saga.messaging.consume.ConsumerConfiguration;
import hr.bertlab.saga.messaging.events.SagaEventCodec;
import hr.bertlab.saga.messaging.ops.MonitoringConfiguration;
import hr.bertlab.saga.messaging.publish.OutboxConfiguration;
import hr.bertlab.saga.messaging.topology.TopologyConfiguration;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@AutoConfiguration(before = RabbitAutoConfiguration.class)
@ConditionalOnProperty(prefix = "saga.messaging", name = "service")
@EnableScheduling
@EnableConfigurationProperties(SagaMessagingProperties.class)
@Import({
    TopologyConfiguration.class,
    OutboxConfiguration.class,
    ConsumerConfiguration.class,
    MonitoringConfiguration.class
})
public class SagaMessagingAutoConfiguration {

    @Bean
    SagaEventCodec sagaEventCodec(JsonMapper jsonMapper) {
        return new SagaEventCodec(jsonMapper);
    }

    @Bean
    TransactionTemplate sagaTransactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}
