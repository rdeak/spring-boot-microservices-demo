package hr.bertlab.saga.messaging.consume;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import hr.bertlab.saga.messaging.SagaService;
import hr.bertlab.saga.messaging.events.EventDecodingException;
import hr.bertlab.saga.messaging.events.EventType;
import hr.bertlab.saga.messaging.events.OrderCreated;
import hr.bertlab.saga.messaging.events.SagaEventCodec;
import hr.bertlab.saga.messaging.events.SagaMessage;
import hr.bertlab.saga.messaging.events.ShippingFailed;
import hr.bertlab.saga.messaging.ops.SagaMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

class SagaMessageDispatcherTest {

    private static SagaHandlers inventoryHandlers() {
        return SagaHandlers.of(SagaService.INVENTORY)
                .on(OrderCreated.class, e -> {})
                .on(ShippingFailed.class, e -> {});
    }

    @Test
    void acceptsACompleteHandlerTable() {
        assertThatCode(() -> dispatcher(SagaService.INVENTORY, inventoryHandlers()))
                .doesNotThrowAnyException();
    }

    @Test
    void failsWhenAConsumedEventHasNoHandler() {
        SagaHandlers incomplete = SagaHandlers.of(SagaService.INVENTORY).on(OrderCreated.class, e -> {});

        assertThatThrownBy(() -> dispatcher(SagaService.INVENTORY, incomplete))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SHIPPING_FAILED");
    }

    @Test
    void failsWhenTheHandlerTableIsForAnotherService() {
        assertThatThrownBy(() -> dispatcher(SagaService.ORDER, inventoryHandlers()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("INVENTORY")
                .hasMessageContaining("ORDER");
    }

    @Test
    void undecodableBodyIsPoison() {
        SagaMessageDispatcher dispatcher = dispatcher(SagaService.INVENTORY, inventoryHandlers());
        MessageProperties properties = new MessageProperties();
        properties.setHeader(SagaMessage.EVENT_ID, UUID.randomUUID().toString());
        properties.setHeader(SagaMessage.ORDER_ID, UUID.randomUUID().toString());
        properties.setHeader(SagaMessage.EVENT_TYPE, EventType.ORDER_CREATED.wireName());
        Message message = new Message("not json".getBytes(StandardCharsets.UTF_8), properties);

        assertThatThrownBy(() -> dispatcher.onMessage(message))
                .isInstanceOf(PoisonMessageException.class)
                .hasCauseInstanceOf(EventDecodingException.class)
                .hasMessageContaining("OrderCreated");
    }

    private static SagaMessageDispatcher dispatcher(SagaService service, SagaHandlers handlers) {
        return new SagaMessageDispatcher(
                service,
                handlers,
                mock(Inbox.class),
                new SagaEventCodec(JsonMapper.builder().build()),
                new TransactionTemplate(),
                new SagaMetrics(new SimpleMeterRegistry()));
    }
}
