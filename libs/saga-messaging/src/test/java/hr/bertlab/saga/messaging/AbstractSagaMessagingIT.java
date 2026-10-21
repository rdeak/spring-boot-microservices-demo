package hr.bertlab.saga.messaging;

import hr.bertlab.saga.messaging.test.SagaTestSupport;
import hr.bertlab.saga.messaging.testapp.TestSagaApplication;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Every library integration test extends this class so they all share one cached context:
 * one schema, one topology prefix, one relay, one listener container. {@code @AutoConfigureTracing}
 * keeps the real trace propagator, which {@code @SpringBootTest} would otherwise replace with a no-op.
 */
@SpringBootTest(classes = TestSagaApplication.class)
@AutoConfigureTracing
public abstract class AbstractSagaMessagingIT {

    @DynamicPropertySource
    static void isolation(DynamicPropertyRegistry registry) {
        SagaTestSupport.registerIsolation(registry, "saga_lib");
    }
}
