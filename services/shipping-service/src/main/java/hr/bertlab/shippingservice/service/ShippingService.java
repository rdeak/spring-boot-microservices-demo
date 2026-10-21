package hr.bertlab.shippingservice.service;

import hr.bertlab.saga.messaging.events.InventoryReserved;
import hr.bertlab.saga.messaging.events.ShippingFailed;
import hr.bertlab.saga.messaging.events.ShippingScheduled;
import hr.bertlab.saga.messaging.publish.Outbox;
import hr.bertlab.shippingservice.model.Carrier;
import hr.bertlab.shippingservice.model.Shipment;
import hr.bertlab.shippingservice.repository.CarrierRepository;
import hr.bertlab.shippingservice.repository.ShipmentRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShippingService {

    private static final Logger log = LoggerFactory.getLogger(ShippingService.class);
    static final int MAX_LIST = 200;

    private final CarrierRepository carriers;
    private final ShipmentRepository shipments;
    private final Outbox outbox;
    private final Clock clock;

    public ShippingService(CarrierRepository carriers, ShipmentRepository shipments, Outbox outbox, Clock clock) {
        this.carriers = carriers;
        this.shipments = shipments;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<Shipment> find(UUID orderId) {
        return shipments.findById(orderId);
    }

    @Transactional(readOnly = true)
    public List<Shipment> list(int limit) {
        return shipments.findAllByOrderByCreatedAtDesc(Limit.of(Math.clamp(limit, 1, MAX_LIST)));
    }

    @Transactional
    public void schedule(InventoryReserved event) {
        if (shipments.existsById(event.orderId())) {
            log.info("Order {} already has a shipment, ignoring InventoryReserved", event.orderId());
            return;
        }
        String country = event.country() == null ? "" : event.country().trim().toUpperCase(Locale.ROOT);
        Optional<Carrier> carrier = carriers.findFirstByCountryOrderByLeadDaysAscNameAsc(country);
        if (carrier.isEmpty()) {
            String reason = "No carrier available for country '" + country + "'";
            shipments.save(Shipment.failed(event.orderId(), reason));
            outbox.append(new ShippingFailed(event.orderId(), reason));
            return;
        }
        LocalDate deliveryDate = LocalDate.now(clock).plusDays(carrier.get().getLeadDays());
        shipments.save(Shipment.scheduled(event.orderId(), carrier.get().getName(), deliveryDate));
        outbox.append(new ShippingScheduled(event.orderId(), carrier.get().getName(), deliveryDate));
    }
}
