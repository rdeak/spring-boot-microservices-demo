package hr.bertlab.saga.messaging.events;

import java.time.LocalDate;
import java.util.UUID;

public record ShippingScheduled(UUID orderId, String carrier, LocalDate deliveryDate) implements SagaEvent {}
