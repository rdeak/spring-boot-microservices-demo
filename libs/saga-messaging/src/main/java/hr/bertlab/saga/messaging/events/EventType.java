package hr.bertlab.saga.messaging.events;

import static hr.bertlab.saga.messaging.SagaService.INVENTORY;
import static hr.bertlab.saga.messaging.SagaService.ORDER;
import static hr.bertlab.saga.messaging.SagaService.SHIPPING;

import hr.bertlab.saga.messaging.SagaService;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public enum EventType {
    ORDER_CREATED(OrderCreated.class, Set.of(INVENTORY), false),
    INVENTORY_RESERVED(InventoryReserved.class, Set.of(SHIPPING), false),
    INVENTORY_RESERVATION_FAILED(InventoryReservationFailed.class, Set.of(ORDER), true),
    SHIPPING_SCHEDULED(ShippingScheduled.class, Set.of(ORDER), false),
    SHIPPING_FAILED(ShippingFailed.class, Set.of(ORDER, INVENTORY), true);

    private final Class<? extends SagaEvent> payloadType;
    private final Set<SagaService> consumers;
    private final boolean compensation;

    EventType(Class<? extends SagaEvent> payloadType, Set<SagaService> consumers, boolean compensation) {
        this.payloadType = payloadType;
        this.consumers = Set.copyOf(consumers);
        this.compensation = compensation;
    }

    public String wireName() {
        return payloadType.getSimpleName();
    }

    public Class<? extends SagaEvent> payloadType() {
        return payloadType;
    }

    public Set<SagaService> consumers() {
        return consumers;
    }

    public boolean isCompensation() {
        return compensation;
    }

    public static Optional<EventType> fromWireName(@Nullable String wireName) {
        return Arrays.stream(values())
                .filter(t -> t.wireName().equals(wireName))
                .findFirst();
    }

    public static EventType of(Class<? extends SagaEvent> payloadType) {
        return Arrays.stream(values())
                .filter(t -> t.payloadType.equals(payloadType))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Not a saga event type: " + payloadType));
    }

    public static EventType of(SagaEvent event) {
        return of(event.getClass());
    }

    public static Set<EventType> consumedBy(SagaService service) {
        EnumSet<EventType> types = EnumSet.noneOf(EventType.class);
        for (EventType type : values()) {
            if (type.consumers.contains(service)) {
                types.add(type);
            }
        }
        return Collections.unmodifiableSet(types);
    }
}
