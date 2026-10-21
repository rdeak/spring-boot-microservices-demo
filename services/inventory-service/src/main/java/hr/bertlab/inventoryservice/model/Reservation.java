package hr.bertlab.inventoryservice.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "reservation")
public class Reservation {

    @Id
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;

    private @Nullable String reason;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "reservation_item", joinColumns = @JoinColumn(name = "order_id"))
    private List<ReservationLine> items = new ArrayList<>();

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Reservation() {}

    public static Reservation reserved(UUID orderId, List<ReservationLine> items) {
        return create(orderId, ReservationStatus.RESERVED, null, items);
    }

    public static Reservation rejected(UUID orderId, String reason) {
        return create(orderId, ReservationStatus.REJECTED, reason, List.of());
    }

    private static Reservation create(
            UUID orderId, ReservationStatus status, @Nullable String reason, List<ReservationLine> items) {
        Reservation reservation = new Reservation();
        reservation.orderId = orderId;
        reservation.status = status;
        reservation.reason = reason;
        reservation.items = new ArrayList<>(items);
        reservation.createdAt = Instant.now();
        reservation.updatedAt = reservation.createdAt;
        return reservation;
    }

    public boolean release() {
        if (status != ReservationStatus.RESERVED) {
            return false;
        }
        status = ReservationStatus.RELEASED;
        updatedAt = Instant.now();
        return true;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public List<ReservationLine> getItems() {
        return List.copyOf(items);
    }

    public @Nullable String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
