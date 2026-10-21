package hr.bertlab.saga.messaging.consume;

import hr.bertlab.saga.messaging.SagaMessagingProperties;
import hr.bertlab.saga.messaging.events.SagaEventCodec;
import hr.bertlab.saga.messaging.ops.AlertService;
import hr.bertlab.saga.messaging.ops.SagaMetrics;
import hr.bertlab.saga.messaging.topology.SagaNames;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.StatelessRetryOperationsInterceptor;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionNameStrategy;
import org.springframework.amqp.rabbit.listener.DirectMessageListenerContainer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.amqp.autoconfigure.CachingConnectionFactoryConfigurer;
import org.springframework.boot.amqp.autoconfigure.RabbitConnectionDetails;
import org.springframework.boot.amqp.autoconfigure.RabbitProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
public class ConsumerConfiguration {

    @Bean
    Inbox sagaInbox(JdbcClient jdbcClient) {
        return new Inbox(jdbcClient);
    }

    @Bean
    SagaMessageDispatcher sagaMessageDispatcher(
            SagaMessagingProperties properties,
            SagaHandlers handlers,
            Inbox inbox,
            SagaEventCodec codec,
            TransactionTemplate sagaTransactionTemplate,
            SagaMetrics metrics) {
        return new SagaMessageDispatcher(
                properties.service(), handlers, inbox, codec, sagaTransactionTemplate, metrics);
    }

    @Bean
    SagaDeadLetterRecoverer sagaDeadLetterRecoverer(AlertService alerts, SagaMetrics metrics) {
        return new SagaDeadLetterRecoverer(alerts, metrics);
    }

    @Bean
    StatelessRetryOperationsInterceptor sagaRetryInterceptor(
            SagaMessagingProperties properties, SagaDeadLetterRecoverer recoverer) {
        SagaMessagingProperties.Retry retry = properties.retry();

        RetryPolicy policy = RetryPolicy.builder()
                .maxRetries(retry.maxAttempts() - 1)
                .delay(retry.initialInterval())
                .multiplier(retry.multiplier())
                .maxDelay(retry.maxInterval())
                .predicate(t -> !SagaDeadLetterRecoverer.isPoison(t))
                .build();

        return RetryInterceptorBuilder.stateless()
                .retryPolicy(policy)
                .recoverer(recoverer)
                .build();
    }

    @Bean
    CachingConnectionFactoryConfigurer rabbitConnectionFactoryConfigurer(
            SagaMessagingProperties properties,
            RabbitProperties rabbitProperties,
            RabbitConnectionDetails connectionDetails,
            ObjectProvider<ConnectionNameStrategy> connectionNameStrategy) {
        SagaConnectionFactoryConfigurer configurer =
                new SagaConnectionFactoryConfigurer(rabbitProperties, connectionDetails, properties.partitions());
        configurer.setConnectionNameStrategy(connectionNameStrategy.getIfUnique());
        return configurer;
    }

    @Bean
    DirectMessageListenerContainer sagaListenerContainer(
            ConnectionFactory connectionFactory,
            SagaNames names,
            SagaMessagingProperties properties,
            SagaMessageDispatcher dispatcher,
            StatelessRetryOperationsInterceptor sagaRetryInterceptor) {
        DirectMessageListenerContainer container = new DirectMessageListenerContainer(connectionFactory);
        container.setQueueNames(names.partitionQueues(properties.service(), properties.partitions())
                .toArray(String[]::new));
        container.setConsumersPerQueue(1);
        container.setPrefetchCount(1);
        container.setAcknowledgeMode(AcknowledgeMode.AUTO);
        container.setDefaultRequeueRejected(false);
        container.setObservationEnabled(true);
        container.setAdviceChain(sagaRetryInterceptor);
        container.setMessageListener(dispatcher);
        return container;
    }
}
