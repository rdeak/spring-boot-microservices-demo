package hr.bertlab.orderservice.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Entity(name = "CustomerOrder")
@Table(name = "orders")
public class Order {

    static final int MAX_ITEMS = 100;
    static final int MAX_QUANTITY = 10_000;
    private static final int MAX_REASON = 500;

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false, length = 2)
    private String country;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "order_item", joinColumns = @JoinColumn(name = "order_id"))
    @OrderColumn(name = "line_no")
    private List<OrderLine> items = new ArrayList<>();

    private String failureReason;

    private Instant alertedStuckAt;

    @Version
    private long version;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Order() {}

    public static Order place(@Nullable String country, List<OrderLine> items) {
        String normalizedCountry = country == null ? "" : country.trim().toUpperCase(Locale.ROOT);
        if (!normalizedCountry.matches("[A-Z]{2}")) {
            throw new InvalidOrderException("country must be a two-letter ISO 3166 code");
        }
        if (items.isEmpty()) {
            throw new InvalidOrderException("items must not be empty");
        }
        if (items.size() > MAX_ITEMS) {
            throw new InvalidOrderException("an order can have at most " + MAX_ITEMS + " items");
        }
        List<OrderLine> normalizedItems = new ArrayList<>();
        for (OrderLine line : items) {
            String sku = line.sku().trim();
            if (sku.isEmpty() || sku.length() > 64) {
                throw new InvalidOrderException("sku must be 1 to 64 characters");
            }
            if (line.quantity() < 1 || line.quantity() > MAX_QUANTITY) {
                throw new InvalidOrderException("quantity must be between 1 and " + MAX_QUANTITY);
            }
            normalizedItems.add(new OrderLine(sku, line.quantity()));
        }
        Order order = new Order();
        order.id = UUID.randomUUID();
        order.status = OrderStatus.PENDING;
        order.country = normalizedCountry;
        order.items = normalizedItems;
        order.createdAt = Instant.now();
        order.updatedAt = order.createdAt;
        return order;
    }

    public boolean confirm() {
        if (status != OrderStatus.PENDING) {
            return false;
        }
        status = OrderStatus.CONFIRMED;
        updatedAt = Instant.now();
        return true;
    }

    public boolean fail(String reason) {
        if (status != OrderStatus.PENDING) {
            return false;
        }
        status = OrderStatus.FAILED;
        failureReason = reason == null || reason.length() <= MAX_REASON ? reason : reason.substring(0, MAX_REASON);
        updatedAt = Instant.now();
        return true;
    }

    public UUID getId() {
        return id;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getCountry() {
        return country;
    }

    public List<OrderLine> getItems() {
        return List.copyOf(items);
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getAlertedStuckAt() {
        return alertedStuckAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
