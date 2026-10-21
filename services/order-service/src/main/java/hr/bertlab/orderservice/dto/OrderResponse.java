package hr.bertlab.orderservice.dto;

import hr.bertlab.orderservice.model.Order;
import hr.bertlab.orderservice.model.OrderLine;
import hr.bertlab.orderservice.model.OrderStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id, OrderStatus status, String country, List<OrderLine> items, String failureReason, Instant createdAt) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getStatus(),
                order.getCountry(),
                order.getItems(),
                order.getFailureReason(),
                order.getCreatedAt());
    }
}
