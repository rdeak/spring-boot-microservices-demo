package hr.bertlab.saga.messaging.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hr.bertlab.saga.messaging.SagaService;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventTypeTest {

    /** Routing keys and the eventType header. Changing a record's name changes the wire, so these stay literal. */
    @Test
    void wireNamesMatchTheContract() {
        assertThat(EventType.ORDER_CREATED.wireName()).isEqualTo("OrderCreated");
        assertThat(EventType.INVENTORY_RESERVED.wireName()).isEqualTo("InventoryReserved");
        assertThat(EventType.INVENTORY_RESERVATION_FAILED.wireName()).isEqualTo("InventoryReservationFailed");
        assertThat(EventType.SHIPPING_SCHEDULED.wireName()).isEqualTo("ShippingScheduled");
        assertThat(EventType.SHIPPING_FAILED.wireName()).isEqualTo("ShippingFailed");
    }

    @Test
    void wireNameIsThePayloadRecordName() {
        for (EventType type : EventType.values()) {
            assertThat(type.wireName()).isEqualTo(type.payloadType().getSimpleName());
        }
    }

    @Test
    void resolvesFromWireNameAndFromPayload() {
        assertThat(EventType.fromWireName("ShippingFailed")).contains(EventType.SHIPPING_FAILED);
        assertThat(EventType.fromWireName("Bogus")).isEmpty();
        assertThat(EventType.fromWireName(null)).isEmpty();
        assertThat(EventType.of(new ShippingFailed(UUID.randomUUID(), "x"))).isEqualTo(EventType.SHIPPING_FAILED);
    }

    @Test
    void onlyFailureEventsAreCompensations() {
        assertThat(EventType.SHIPPING_FAILED.isCompensation()).isTrue();
        assertThat(EventType.INVENTORY_RESERVATION_FAILED.isCompensation()).isTrue();
        assertThat(EventType.ORDER_CREATED.isCompensation()).isFalse();
        assertThat(EventType.INVENTORY_RESERVED.isCompensation()).isFalse();
        assertThat(EventType.SHIPPING_SCHEDULED.isCompensation()).isFalse();
    }

    @Test
    void consumersMatchTheSagaChoreography() {
        assertThat(EventType.ORDER_CREATED.consumers()).containsExactly(SagaService.INVENTORY);
        assertThat(EventType.INVENTORY_RESERVED.consumers()).containsExactly(SagaService.SHIPPING);
        assertThat(EventType.INVENTORY_RESERVATION_FAILED.consumers()).containsExactly(SagaService.ORDER);
        assertThat(EventType.SHIPPING_SCHEDULED.consumers()).containsExactly(SagaService.ORDER);
        assertThat(EventType.SHIPPING_FAILED.consumers())
                .containsExactlyInAnyOrder(SagaService.ORDER, SagaService.INVENTORY);
    }

    @Test
    void consumedByListsEachServicesSubscriptions() {
        assertThat(EventType.consumedBy(SagaService.INVENTORY))
                .containsExactlyInAnyOrder(EventType.ORDER_CREATED, EventType.SHIPPING_FAILED);
        assertThat(EventType.consumedBy(SagaService.SHIPPING)).containsExactly(EventType.INVENTORY_RESERVED);
        assertThat(EventType.consumedBy(SagaService.ORDER))
                .containsExactlyInAnyOrder(
                        EventType.INVENTORY_RESERVATION_FAILED,
                        EventType.SHIPPING_SCHEDULED,
                        EventType.SHIPPING_FAILED);
    }

    @Test
    void everyServiceConsumesSomething() {
        for (SagaService service : SagaService.values()) {
            assertThat(EventType.consumedBy(service))
                    .as("%s subscriptions", service)
                    .isNotEmpty();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void ofRejectsUnknownClasses() {
        assertThatThrownBy(() -> EventType.of((Class<? extends SagaEvent>) (Class<?>) SagaEvent.class))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
