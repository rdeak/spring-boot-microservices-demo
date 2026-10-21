package hr.bertlab.saga.messaging.events;

import java.util.List;
import java.util.UUID;

public record OrderCreated(UUID orderId, List<OrderItem> items, String country) implements SagaEvent {}
