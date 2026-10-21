package hr.bertlab.saga.messaging.consume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hr.bertlab.saga.messaging.SagaService;
import hr.bertlab.saga.messaging.events.EventType;
import hr.bertlab.saga.messaging.events.InventoryReserved;
import hr.bertlab.saga.messaging.events.OrderCreated;
import hr.bertlab.saga.messaging.events.SagaEvent;
import hr.bertlab.saga.messaging.events.ShippingFailed;
import hr.bertlab.saga.messaging.events.ShippingScheduled;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SagaHandlersTest {

    @Test
    void registersHandlersForTheEventsTheServiceConsumes() {
        SagaHandlers handlers = SagaHandlers.of(SagaService.INVENTORY)
                .on(OrderCreated.class, e -> {})
                .on(ShippingFailed.class, e -> {});

        assertThat(handlers.service()).isEqualTo(SagaService.INVENTORY);
        assertThat(handlers.types()).containsExactlyInAnyOrder(EventType.ORDER_CREATED, EventType.SHIPPING_FAILED);
    }

    @Test
    void handleCallsTheRegisteredHandlerWithTheEvent() {
        List<SagaEvent> seen = new ArrayList<>();
        SagaHandlers handlers = SagaHandlers.of(SagaService.SHIPPING).on(InventoryReserved.class, seen::add);
        InventoryReserved event = new InventoryReserved(UUID.randomUUID(), List.of(), "HR");

        handlers.handle(EventType.INVENTORY_RESERVED, event);

        assertThat(seen).containsExactly(event);
    }

    @Test
    void rejectsADuplicateRegistration() {
        assertThatThrownBy(() -> SagaHandlers.of(SagaService.INVENTORY)
                        .on(OrderCreated.class, e -> {})
                        .on(OrderCreated.class, e -> {}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate handler for OrderCreated");
    }

    @Test
    void rejectsAnEventTheServiceDoesNotConsume() {
        assertThatThrownBy(() -> SagaHandlers.of(SagaService.INVENTORY).on(ShippingScheduled.class, e -> {}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("INVENTORY does not consume ShippingScheduled")
                .hasMessageContaining("ORDER");
    }

    @Test
    void handleRejectsAnUnregisteredType() {
        SagaHandlers handlers = SagaHandlers.of(SagaService.INVENTORY).on(OrderCreated.class, e -> {});

        assertThatThrownBy(() -> handlers.handle(EventType.SHIPPING_FAILED, new ShippingFailed(UUID.randomUUID(), "x")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ShippingFailed");
    }
}
