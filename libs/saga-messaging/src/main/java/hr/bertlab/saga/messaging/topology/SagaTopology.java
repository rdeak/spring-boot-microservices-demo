package hr.bertlab.saga.messaging.topology;

import hr.bertlab.saga.messaging.SagaService;
import hr.bertlab.saga.messaging.events.EventType;
import hr.bertlab.saga.messaging.events.SagaMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;

/**
 * The complete saga topology, derived from {@link EventType}. Every service declares all of it
 * (declarations are idempotent), so a message is never silently routed to only some of its
 * subscribers because one of them has not started yet.
 *
 * <pre>
 * saga.events (topic) --[wire name]--> {service}.in (x-consistent-hash on orderId) --> {service}.q.0 .. q.N-1
 *                                                                        on failure --> {service}.dlx --> {service}.dlq
 * </pre>
 */
public final class SagaTopology {

    private SagaTopology() {}

    public static Declarables declarables(SagaNames names, int partitions) {
        List<Declarable> declarables = new ArrayList<>();

        TopicExchange events = ExchangeBuilder.topicExchange(names.eventsExchange())
                .durable(true)
                .build();
        declarables.add(events);

        for (SagaService service : SagaService.values()) {
            Exchange in = ExchangeBuilder.consistentHashExchange(names.inExchange(service))
                    .hashHeader(SagaMessage.ORDER_ID)
                    .durable(true)
                    .build();
            declarables.add(in);

            for (EventType type : EventType.consumedBy(service)) {
                declarables.add(BindingBuilder.bind(in).to(events).with(type.wireName()));
            }

            FanoutExchange dlx = ExchangeBuilder.fanoutExchange(names.deadLetterExchange(service))
                    .durable(true)
                    .build();
            Queue dlq = QueueBuilder.durable(names.deadLetterQueue(service))
                    .quorum()
                    .build();
            declarables.add(dlx);
            declarables.add(dlq);
            declarables.add(BindingBuilder.bind(dlq).to(dlx));

            for (int partition = 0; partition < partitions; partition++) {
                Queue queue = QueueBuilder.durable(names.partitionQueue(service, partition))
                        .quorum()
                        .singleActiveConsumer()
                        .deadLetterExchange(names.deadLetterExchange(service))
                        .build();
                declarables.add(queue);
                // Consistent-hash bindings use the routing key as the bucket weight.
                declarables.add(
                        new Binding(queue.getName(), Binding.DestinationType.QUEUE, in.getName(), "1", Map.of()));
            }
        }
        return new Declarables(declarables);
    }
}
