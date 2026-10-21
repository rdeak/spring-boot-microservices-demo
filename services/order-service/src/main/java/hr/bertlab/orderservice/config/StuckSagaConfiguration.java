package hr.bertlab.orderservice.config;

import hr.bertlab.orderservice.service.StuckOrderDetector;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(StuckSagaProperties.class)
class StuckSagaConfiguration {

    @Bean
    SchedulingConfigurer stuckOrderSchedule(StuckOrderDetector detector, StuckSagaProperties properties) {
        return registrar -> registrar.addFixedDelayTask(detector::detect, properties.checkInterval());
    }
}
