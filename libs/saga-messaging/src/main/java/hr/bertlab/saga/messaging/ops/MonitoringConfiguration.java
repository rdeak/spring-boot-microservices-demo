package hr.bertlab.saga.messaging.ops;

import hr.bertlab.saga.messaging.SagaMessagingProperties;
import hr.bertlab.saga.messaging.publish.Outbox;
import hr.bertlab.saga.messaging.topology.SagaNames;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;

/**
 * Alerts, metrics, the DLQ and outbox monitor, and dead-letter replay. None of it is on the message
 * path; {@code consume} only reports into {@link AlertService} and {@link SagaMetrics}. Public so the
 * root auto-configuration can import it.
 */
@Configuration(proxyBeanMethods = false)
public class MonitoringConfiguration {

    @Bean
    AlertService sagaAlertService(MeterRegistry registry) {
        return new AlertService(registry);
    }

    @Bean
    SagaMetrics sagaMetrics(MeterRegistry registry) {
        return new SagaMetrics(registry);
    }

    @Bean
    SagaMonitor sagaMonitor(
            AmqpAdmin amqpAdmin,
            Outbox outbox,
            AlertService alerts,
            SagaNames names,
            SagaMessagingProperties properties,
            MeterRegistry registry) {
        return new SagaMonitor(
                amqpAdmin,
                outbox,
                alerts,
                names.deadLetterQueue(properties.service()),
                properties.monitor().outboxLagThreshold(),
                registry);
    }

    @Bean
    SchedulingConfigurer sagaMonitorSchedule(SagaMonitor monitor, SagaMessagingProperties properties) {
        return registrar ->
                registrar.addFixedDelayTask(monitor::poll, properties.monitor().pollInterval());
    }

    @Bean
    DlqReplayer sagaDlqReplayer(RabbitTemplate rabbitTemplate, SagaNames names, SagaMessagingProperties properties) {
        return new DlqReplayer(
                rabbitTemplate, names.deadLetterQueue(properties.service()), names.inExchange(properties.service()));
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(prefix = "saga.messaging", name = "admin-path")
    static class AdminEndpointConfiguration {

        @Bean
        DlqReplayController sagaDlqReplayController(DlqReplayer replayer) {
            return new DlqReplayController(replayer);
        }
    }
}
