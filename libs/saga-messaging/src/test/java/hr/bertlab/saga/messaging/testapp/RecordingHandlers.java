package hr.bertlab.saga.messaging.testapp;

import hr.bertlab.saga.messaging.SagaService;
import hr.bertlab.saga.messaging.consume.SagaHandlers;
import hr.bertlab.saga.messaging.events.OrderCreated;
import hr.bertlab.saga.messaging.events.SagaEvent;
import hr.bertlab.saga.messaging.events.ShippingFailed;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/**
 * Records successfully handled events and can be told to fail for specific orders.
 */
@Component
public class RecordingHandlers {

    private static final int ALWAYS = Integer.MAX_VALUE;

    private final List<SagaEvent> received = new CopyOnWriteArrayList<>();
    private final Map<UUID, AtomicInteger> attempts = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> failuresRemaining = new ConcurrentHashMap<>();
    private final Map<UUID, String> threads = new ConcurrentHashMap<>();
    private final Map<UUID, String> traceIds = new ConcurrentHashMap<>();

    public void failTimes(UUID orderId, int times) {
        failuresRemaining.put(orderId, times);
    }

    public void failAlways(UUID orderId) {
        failuresRemaining.put(orderId, ALWAYS);
    }

    public void stopFailing(UUID orderId) {
        failuresRemaining.remove(orderId);
    }

    public List<SagaEvent> receivedFor(UUID orderId) {
        return received.stream().filter(e -> e.orderId().equals(orderId)).toList();
    }

    public int attemptsFor(UUID orderId) {
        AtomicInteger count = attempts.get(orderId);
        return count == null ? 0 : count.get();
    }

    public String threadFor(UUID orderId) {
        return threads.getOrDefault(orderId, "");
    }

    /** The MDC trace id while the order's last event was handled, or an empty string. */
    public String traceIdFor(UUID orderId) {
        return traceIds.getOrDefault(orderId, "");
    }

    void handle(SagaEvent event) {
        attempts.computeIfAbsent(event.orderId(), id -> new AtomicInteger()).incrementAndGet();
        threads.put(event.orderId(), Thread.currentThread().getName());
        traceIds.put(event.orderId(), String.valueOf(MDC.get("traceId")));
        Integer remaining = failuresRemaining.get(event.orderId());
        if (remaining != null && remaining > 0) {
            if (remaining != ALWAYS) {
                failuresRemaining.put(event.orderId(), remaining - 1);
            }
            throw new IllegalStateException("Simulated failure for order " + event.orderId());
        }
        received.add(event);
    }

    @Configuration(proxyBeanMethods = false)
    static class Handlers {

        @Bean
        SagaHandlers inventoryHandlers(RecordingHandlers recorder) {
            return SagaHandlers.of(SagaService.INVENTORY)
                    .on(OrderCreated.class, recorder::handle)
                    .on(ShippingFailed.class, recorder::handle);
        }
    }
}
