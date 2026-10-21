package hr.bertlab.saga.messaging.publish;

import hr.bertlab.saga.messaging.SagaMessagingProperties;
import hr.bertlab.saga.messaging.events.SagaEventCodec;
import hr.bertlab.saga.messaging.topology.SagaNames;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.amqp.autoconfigure.RabbitTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
public class OutboxConfiguration {

    @Bean
    OutboxTraceContext outboxTraceContext(ObjectProvider<Tracer> tracer, ObjectProvider<Propagator> propagator) {
        return new OutboxTraceContext(
                tracer.getIfAvailable(() -> Tracer.NOOP), propagator.getIfAvailable(() -> Propagator.NOOP));
    }

    @Bean
    Outbox outbox(JdbcClient jdbcClient, SagaEventCodec codec, OutboxTraceContext tracing, JsonMapper jsonMapper) {
        return new Outbox(jdbcClient, codec, tracing, jsonMapper);
    }

    @Bean
    RabbitTemplateCustomizer sagaMandatoryRabbitTemplate() {
        return template -> {
            template.setMandatory(true);
            template.setObservationEnabled(true);
        };
    }

    @Bean
    OutboxRelay outboxRelay(
            Outbox outbox,
            RabbitTemplate rabbitTemplate,
            TransactionTemplate sagaTransactionTemplate,
            SagaNames names,
            OutboxTraceContext tracing,
            SagaMessagingProperties properties) {
        String lockKey = "saga-outbox-relay:" + properties.prefix()
                + properties.service().key();
        return new OutboxRelay(
                outbox, rabbitTemplate, sagaTransactionTemplate, names, tracing, lockKey, properties.relay());
    }

    @Bean
    SchedulingConfigurer sagaOutboxSchedules(OutboxRelay relay, SagaMessagingProperties properties) {
        return registrar -> {
            registrar.addFixedDelayTask(
                    relay::publishPending, properties.relay().pollInterval());
            registrar.addCronTask(relay::purgePublished, "0 0 3 * * *");
        };
    }
}
