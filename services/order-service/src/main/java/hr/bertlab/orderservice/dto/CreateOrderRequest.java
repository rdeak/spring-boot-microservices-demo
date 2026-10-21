package hr.bertlab.orderservice.dto;

import static java.util.Objects.requireNonNullElse;

import hr.bertlab.orderservice.model.OrderLine;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record CreateOrderRequest(
        @Nullable String country, @Nullable List<Item> items) {

    public record Item(@Nullable String sku, @Nullable Integer quantity) {}

    public List<OrderLine> toLines() {
        if (items == null) {
            return List.of();
        }
        return items.stream().map(CreateOrderRequest::toLine).toList();
    }

    private static OrderLine toLine(@Nullable Item item) {
        if (item == null) {
            return new OrderLine("", 0);
        }
        return new OrderLine(requireNonNullElse(item.sku(), ""), requireNonNullElse(item.quantity(), 0));
    }
}
