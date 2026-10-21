package hr.bertlab.saga.messaging.events;

import java.util.UUID;

public sealed interface SagaEvent
        permits OrderCreated, InventoryReserved, InventoryReservationFailed, ShippingScheduled, ShippingFailed {

    UUID orderId();
}
