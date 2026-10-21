package hr.bertlab.saga.messaging.events;

import java.util.UUID;

public record InventoryReservationFailed(UUID orderId, String reason) implements SagaEvent {}
