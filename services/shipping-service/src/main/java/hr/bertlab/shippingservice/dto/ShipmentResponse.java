package hr.bertlab.shippingservice.dto;

import hr.bertlab.shippingservice.model.Shipment;
import hr.bertlab.shippingservice.model.ShipmentStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ShipmentResponse(
        UUID orderId, ShipmentStatus status, String carrier, LocalDate deliveryDate, String reason, Instant createdAt) {

    public static ShipmentResponse from(Shipment shipment) {
        return new ShipmentResponse(
                shipment.getOrderId(),
                shipment.getStatus(),
                shipment.getCarrier(),
                shipment.getDeliveryDate(),
                shipment.getReason(),
                shipment.getCreatedAt());
    }
}
