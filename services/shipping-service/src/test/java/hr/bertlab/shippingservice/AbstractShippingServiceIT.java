package hr.bertlab.shippingservice;

import hr.bertlab.saga.messaging.test.SagaTestSupport;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractShippingServiceIT {

    @DynamicPropertySource
    static void isolation(DynamicPropertyRegistry registry) {
        SagaTestSupport.registerIsolation(registry, "shipping");
    }
}
