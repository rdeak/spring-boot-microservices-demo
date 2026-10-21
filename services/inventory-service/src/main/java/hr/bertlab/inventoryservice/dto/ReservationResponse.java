package hr.bertlab.inventoryservice.dto;

import hr.bertlab.inventoryservice.model.Reservation;
import hr.bertlab.inventoryservice.model.ReservationLine;
import hr.bertlab.inventoryservice.model.ReservationStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record ReservationResponse(
        UUID orderId,
        ReservationStatus status,
        @Nullable String reason,
        List<ReservationLine> items,
        Instant createdAt,
        Instant updatedAt) {

    public static ReservationResponse from(Reservation reservation) {
        return new ReservationResponse(
                reservation.getOrderId(),
                reservation.getStatus(),
                reservation.getReason(),
                reservation.getItems(),
                reservation.getCreatedAt(),
                reservation.getUpdatedAt());
    }
}
