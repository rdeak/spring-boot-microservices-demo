package hr.bertlab.saga.messaging.test;

import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Declarables;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Registers {@link TestIsolationCleanup} in every context that has the test fixtures on its classpath. */
@AutoConfiguration
public class TestIsolationCleanupAutoConfiguration {

    @Bean
    TestIsolationCleanup testIsolationCleanup(
            JdbcClient jdbc,
            AmqpAdmin admin,
            ObjectProvider<Declarables> declarables,
            @Value("${app.schema:}") String schema,
            @Value("${saga.messaging.prefix:}") String prefix) {
        return new TestIsolationCleanup(jdbc, admin, declarables.orderedStream().toList(), schema, prefix);
    }
}
