package hr.bertlab.saga.messaging.events;

import java.util.UUID;

public record ShippingFailed(UUID orderId, String reason) implements SagaEvent {}
