package hr.bertlab.saga.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SagaServiceTest {

    @Test
    void keyIsTheLowerCaseNameUsedInBrokerObjectNames() {
        assertThat(SagaService.ORDER.key()).isEqualTo("order");
        assertThat(SagaService.INVENTORY.key()).isEqualTo("inventory");
        assertThat(SagaService.SHIPPING.key()).isEqualTo("shipping");
    }
}
