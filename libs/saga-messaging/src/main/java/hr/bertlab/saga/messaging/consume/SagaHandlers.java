package hr.bertlab.saga.messaging.consume;

import hr.bertlab.saga.messaging.SagaService;
import hr.bertlab.saga.messaging.events.EventType;
import hr.bertlab.saga.messaging.events.SagaEvent;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * What one service does with each event it consumes. Register exactly one bean per service, built
 * inside its {@code @Bean} method:
 *
 * <pre>{@code
 * @Bean
 * SagaHandlers inventoryHandlers(InventoryService inventory) {
 *     return SagaHandlers.of(SagaService.INVENTORY)
 *             .on(OrderCreated.class, inventory::reserve)
 *             .on(ShippingFailed.class, inventory::release);
 * }
 * }</pre>
 *
 * A handler runs inside the inbox transaction: write domain changes and outbox events there, and
 * throw to roll everything back and trigger a retry. {@link SagaMessageDispatcher} checks at startup
 * that every event the service consumes according to {@link EventType} has a handler.
 */
public final class SagaHandlers {

    private final SagaService service;
    private final Map<EventType, Consumer<SagaEvent>> handlers = new EnumMap<>(EventType.class);

    private SagaHandlers(SagaService service) {
        this.service = service;
    }

    public static SagaHandlers of(SagaService service) {
        return new SagaHandlers(service);
    }

    /**
     * Registers the handler for one event type. Fails at once for a duplicate, or for an event this
     * service does not consume according to {@link EventType}.
     */
    @SuppressWarnings("unchecked")
    public <E extends SagaEvent> SagaHandlers on(Class<E> eventClass, Consumer<E> handler) {
        EventType type = EventType.of(eventClass);
        if (!type.consumers().contains(service)) {
            throw new IllegalStateException(
                    service + " does not consume " + type.wireName() + ", it is consumed by " + type.consumers());
        }
        // Safe: the dispatcher decodes every payload as type.payloadType() before calling handle.
        if (handlers.putIfAbsent(type, (Consumer<SagaEvent>) handler) != null) {
            throw new IllegalStateException("Duplicate handler for " + type.wireName());
        }
        return this;
    }

    public SagaService service() {
        return service;
    }

    /** The event types registered so far. */
    public Set<EventType> types() {
        return Collections.unmodifiableSet(handlers.keySet());
    }

    public void handle(EventType type, SagaEvent event) {
        Consumer<SagaEvent> handler = handlers.get(type);
        if (handler == null) {
            throw new IllegalArgumentException(service + " has no handler for " + type.wireName());
        }
        handler.accept(event);
    }
}
