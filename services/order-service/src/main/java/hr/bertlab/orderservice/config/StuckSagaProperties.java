package hr.bertlab.orderservice.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.stuck-saga")
public record StuckSagaProperties(
        @DefaultValue("5m") Duration threshold,
        @DefaultValue("60s") Duration checkInterval) {}
