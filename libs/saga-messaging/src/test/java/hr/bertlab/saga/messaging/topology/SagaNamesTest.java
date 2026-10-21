package hr.bertlab.saga.messaging.topology;

import static org.assertj.core.api.Assertions.assertThat;

import hr.bertlab.saga.messaging.SagaService;
import org.junit.jupiter.api.Test;

class SagaNamesTest {

    @Test
    void appliesThePrefixToEveryName() {
        SagaNames names = new SagaNames("t1.");
        assertThat(names.eventsExchange()).isEqualTo("t1.saga.events");
        assertThat(names.inExchange(SagaService.INVENTORY)).isEqualTo("t1.inventory.in");
        assertThat(names.partitionQueue(SagaService.INVENTORY, 2)).isEqualTo("t1.inventory.q.2");
        assertThat(names.partitionQueues(SagaService.ORDER, 3))
                .containsExactly("t1.order.q.0", "t1.order.q.1", "t1.order.q.2");
        assertThat(names.deadLetterExchange(SagaService.SHIPPING)).isEqualTo("t1.shipping.dlx");
        assertThat(names.deadLetterQueue(SagaService.SHIPPING)).isEqualTo("t1.shipping.dlq");
    }

    @Test
    void nullPrefixMeansNoPrefix() {
        assertThat(new SagaNames(null).eventsExchange()).isEqualTo("saga.events");
    }
}
