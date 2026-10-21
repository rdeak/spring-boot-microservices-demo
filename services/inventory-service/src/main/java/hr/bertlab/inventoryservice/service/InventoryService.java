package hr.bertlab.inventoryservice.service;

import hr.bertlab.inventoryservice.model.Reservation;
import hr.bertlab.inventoryservice.model.ReservationLine;
import hr.bertlab.inventoryservice.model.Stock;
import hr.bertlab.inventoryservice.repository.ReservationRepository;
import hr.bertlab.inventoryservice.repository.StockRepository;
import hr.bertlab.saga.messaging.events.InventoryReservationFailed;
import hr.bertlab.saga.messaging.events.InventoryReserved;
import hr.bertlab.saga.messaging.events.OrderCreated;
import hr.bertlab.saga.messaging.events.OrderItem;
import hr.bertlab.saga.messaging.events.ShippingFailed;
import hr.bertlab.saga.messaging.publish.Outbox;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);
    static final int MAX_LIST = 200;

    private final StockRepository stock;
    private final ReservationRepository reservations;
    private final Outbox outbox;

    public InventoryService(StockRepository stock, ReservationRepository reservations, Outbox outbox) {
        this.stock = stock;
        this.reservations = reservations;
        this.outbox = outbox;
    }

    @Transactional(readOnly = true)
    public Optional<Stock> findStock(String sku) {
        return stock.findById(sku);
    }

    @Transactional(readOnly = true)
    public List<Stock> listStock() {
        return stock.findAll(Sort.by("sku"));
    }

    @Transactional(readOnly = true)
    public List<Reservation> listReservations(int limit) {
        return reservations.findAllByOrderByCreatedAtDesc(Limit.of(Math.clamp(limit, 1, MAX_LIST)));
    }

    @Transactional
    public void reserve(OrderCreated event) {
        if (reservations.existsById(event.orderId())) {
            log.info("Order {} already has a reservation, ignoring OrderCreated", event.orderId());
            return;
        }
        Map<String, Integer> requested = aggregate(event.items());
        Map<String, Stock> locked = lock(requested);

        Optional<String> problem = requested.isEmpty()
                ? Optional.of("Order has no items")
                : requested.entrySet().stream()
                        .map(line -> problemWith(line.getKey(), line.getValue(), locked.get(line.getKey())))
                        .flatMap(Optional::stream)
                        .findFirst();
        if (problem.isPresent()) {
            reservations.save(Reservation.rejected(event.orderId(), problem.get()));
            outbox.append(new InventoryReservationFailed(event.orderId(), problem.get()));
            return;
        }

        requested.forEach((sku, quantity) -> lockedStock(locked, sku).reserve(quantity));
        List<ReservationLine> lines = requested.entrySet().stream()
                .map(line -> new ReservationLine(line.getKey(), line.getValue()))
                .toList();
        reservations.save(Reservation.reserved(event.orderId(), lines));
        outbox.append(new InventoryReserved(
                event.orderId(),
                lines.stream()
                        .map(line -> new OrderItem(line.sku(), line.quantity()))
                        .toList(),
                event.country()));
    }

    @Transactional
    public void release(ShippingFailed event) {
        Reservation reservation = reservations
                .findForUpdate(event.orderId())
                .orElseThrow(() -> new IllegalStateException(
                        "No reservation for order " + event.orderId() + ", cannot compensate ShippingFailed"));
        if (!reservation.release()) {
            log.info("Reservation for order {} is {}, nothing to release", event.orderId(), reservation.getStatus());
            return;
        }
        Map<String, Integer> reserved = reservation.getItems().stream()
                .collect(Collectors.toMap(ReservationLine::sku, ReservationLine::quantity));
        Map<String, Stock> locked = lock(reserved);
        reserved.forEach((sku, quantity) -> lockedStock(locked, sku).release(quantity));
    }

    private Map<String, Stock> lock(Map<String, Integer> quantitiesBySku) {
        if (quantitiesBySku.isEmpty()) {
            return Map.of();
        }
        return stock.lockAll(quantitiesBySku.keySet()).stream()
                .collect(Collectors.toMap(Stock::getSku, Function.identity()));
    }

    private static Stock lockedStock(Map<String, Stock> locked, String sku) {
        Stock stock = locked.get(sku);
        if (stock == null) {
            throw new IllegalStateException("No stock row for SKU " + sku);
        }
        return stock;
    }

    private static Map<String, Integer> aggregate(List<OrderItem> items) {
        Map<String, Integer> bySku = new TreeMap<>();
        if (items != null) {
            for (OrderItem item : items) {
                bySku.merge(item.sku(), item.quantity(), Integer::sum);
            }
        }
        return bySku;
    }

    private static Optional<String> problemWith(String sku, int quantity, @Nullable Stock stock) {
        if (quantity <= 0) {
            return Optional.of("Invalid quantity " + quantity + " for " + sku);
        }
        if (stock == null) {
            return Optional.of("Unknown SKU " + sku);
        }
        if (!stock.canReserve(quantity)) {
            return Optional.of("Insufficient stock for " + sku + ": requested " + quantity + ", available "
                    + stock.getAvailable());
        }
        return Optional.empty();
    }
}
