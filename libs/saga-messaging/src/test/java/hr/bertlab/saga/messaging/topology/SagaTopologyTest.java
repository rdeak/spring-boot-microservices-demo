package hr.bertlab.saga.messaging.topology;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;

class SagaTopologyTest {

    private final SagaNames names = new SagaNames("p.");
    private final Declarables declarables = SagaTopology.declarables(names, 2);

    @Test
    void declaresTopicExchangeAndAConsistentHashExchangePerService() {
        List<Exchange> exchanges = declarables.getDeclarablesByType(Exchange.class);
        assertThat(exchanges)
                .extracting(Exchange::getName)
                .containsExactlyInAnyOrder(
                        "p.saga.events",
                        "p.inventory.in",
                        "p.order.in",
                        "p.shipping.in",
                        "p.inventory.dlx",
                        "p.order.dlx",
                        "p.shipping.dlx");
        Exchange events = byName(exchanges, "p.saga.events");
        assertThat(events.getType()).isEqualTo("topic");
        Exchange inventoryIn = byName(exchanges, "p.inventory.in");
        assertThat(inventoryIn.getType()).isEqualTo("x-consistent-hash");
        assertThat(inventoryIn.getArguments()).containsEntry("hash-header", "orderId");
        assertThat(byName(exchanges, "p.inventory.dlx").getType()).isEqualTo("fanout");
        assertThat(exchanges).allMatch(Exchange::isDurable);
    }

    @Test
    void partitionQueuesAreQuorumSingleActiveConsumerWithDeadLettering() {
        List<Queue> queues = declarables.getDeclarablesByType(Queue.class);
        Queue q = queues.stream()
                .filter(it -> it.getName().equals("p.inventory.q.1"))
                .findFirst()
                .orElseThrow();
        assertThat(q.isDurable()).isTrue();
        assertThat(q.getArguments())
                .containsEntry("x-queue-type", "quorum")
                .containsEntry("x-single-active-consumer", true)
                .containsEntry("x-dead-letter-exchange", "p.inventory.dlx");
        assertThat(queues)
                .extracting(Queue::getName)
                .contains(
                        "p.inventory.q.0",
                        "p.inventory.q.1",
                        "p.inventory.dlq",
                        "p.order.q.0",
                        "p.order.q.1",
                        "p.order.dlq",
                        "p.shipping.q.0",
                        "p.shipping.q.1",
                        "p.shipping.dlq");
    }

    @Test
    void bindsSubscribedEventTypesAndPartitionsWithWeightOne() {
        List<Binding> bindings = declarables.getDeclarablesByType(Binding.class);
        assertThat(bindings)
                .filteredOn(b -> "p.inventory.in".equals(b.getDestination()))
                .extracting(Binding::getRoutingKey)
                .containsExactlyInAnyOrder("OrderCreated", "ShippingFailed");
        assertThat(bindings)
                .filteredOn(b -> "p.inventory.in".equals(b.getExchange()))
                .extracting(Binding::getDestination, Binding::getRoutingKey)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("p.inventory.q.0", "1"),
                        org.assertj.core.groups.Tuple.tuple("p.inventory.q.1", "1"));
        assertThat(bindings)
                .filteredOn(b -> "p.inventory.dlx".equals(b.getExchange()))
                .extracting(Binding::getDestination)
                .containsExactly("p.inventory.dlq");
    }

    private static Exchange byName(List<Exchange> exchanges, String name) {
        return exchanges.stream()
                .filter(e -> e.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
