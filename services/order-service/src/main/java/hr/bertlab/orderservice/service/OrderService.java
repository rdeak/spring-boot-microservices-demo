package hr.bertlab.orderservice.service;

import hr.bertlab.orderservice.model.Order;
import hr.bertlab.orderservice.model.OrderLine;
import hr.bertlab.orderservice.repository.OrderRepository;
import hr.bertlab.saga.messaging.events.OrderCreated;
import hr.bertlab.saga.messaging.events.OrderItem;
import hr.bertlab.saga.messaging.publish.Outbox;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    static final int MAX_LIST = 200;

    private final OrderRepository orders;
    private final Outbox outbox;

    public OrderService(OrderRepository orders, Outbox outbox) {
        this.orders = orders;
        this.outbox = outbox;
    }

    @Transactional
    public Order place(@Nullable String country, List<OrderLine> items) {
        Order order = orders.save(Order.place(country, items));
        outbox.append(new OrderCreated(
                order.getId(),
                order.getItems().stream()
                        .map(line -> new OrderItem(line.sku(), line.quantity()))
                        .toList(),
                order.getCountry()));
        return order;
    }

    @Transactional(readOnly = true)
    public Optional<Order> find(UUID id) {
        return orders.findById(id);
    }

    @Transactional(readOnly = true)
    public List<Order> list(int limit) {
        return orders.findAllByOrderByCreatedAtDesc(Limit.of(Math.clamp(limit, 1, MAX_LIST)));
    }

    @Transactional
    public void confirm(UUID id) {
        Order order = require(id);
        if (!order.confirm()) {
            log.info("Ignoring ShippingScheduled for order {} in status {}", id, order.getStatus());
        }
    }

    @Transactional
    public void fail(UUID id, String reason) {
        Order order = require(id);
        if (!order.fail(reason)) {
            log.info("Ignoring failure '{}' for order {} in status {}", reason, id, order.getStatus());
        }
    }

    private Order require(UUID id) {
        return orders.findById(id).orElseThrow(() -> new IllegalStateException("Unknown order " + id));
    }
}
