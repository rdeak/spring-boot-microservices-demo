package hr.bertlab.shippingservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "shipment")
public class Shipment {

    private static final int MAX_REASON = 500;

    @Id
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShipmentStatus status;

    private String carrier;

    private LocalDate deliveryDate;

    private String reason;

    @Column(nullable = false)
    private Instant createdAt;

    protected Shipment() {}

    public static Shipment scheduled(UUID orderId, String carrier, LocalDate deliveryDate) {
        Shipment shipment = new Shipment();
        shipment.orderId = orderId;
        shipment.status = ShipmentStatus.SCHEDULED;
        shipment.carrier = carrier;
        shipment.deliveryDate = deliveryDate;
        shipment.createdAt = Instant.now();
        return shipment;
    }

    public static Shipment failed(UUID orderId, String reason) {
        Shipment shipment = new Shipment();
        shipment.orderId = orderId;
        shipment.status = ShipmentStatus.FAILED;
        shipment.reason = reason.length() <= MAX_REASON ? reason : reason.substring(0, MAX_REASON);
        shipment.createdAt = Instant.now();
        return shipment;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public ShipmentStatus getStatus() {
        return status;
    }

    public String getCarrier() {
        return carrier;
    }

    public LocalDate getDeliveryDate() {
        return deliveryDate;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
