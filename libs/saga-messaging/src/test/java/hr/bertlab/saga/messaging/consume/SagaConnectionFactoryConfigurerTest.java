package hr.bertlab.saga.messaging.consume;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ThreadPoolExecutor;
import org.junit.jupiter.api.Test;

class SagaConnectionFactoryConfigurerTest {

    @Test
    void listenerPoolHasAThreadPerPartitionPlusSpareForConfirms() {
        ThreadPoolExecutor pool = SagaConnectionFactoryConfigurer.listenerPool(4);
        try {
            assertThat(pool.getCorePoolSize()).isEqualTo(6);
            assertThat(pool.getMaximumPoolSize()).isEqualTo(6);
        } finally {
            pool.shutdown();
        }
    }
}
