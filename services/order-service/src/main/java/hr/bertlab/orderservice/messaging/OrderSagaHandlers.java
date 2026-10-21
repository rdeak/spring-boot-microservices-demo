package hr.bertlab.orderservice.messaging;

import hr.bertlab.orderservice.service.OrderService;
import hr.bertlab.saga.messaging.SagaService;
import hr.bertlab.saga.messaging.consume.SagaHandlers;
import hr.bertlab.saga.messaging.events.InventoryReservationFailed;
import hr.bertlab.saga.messaging.events.ShippingFailed;
import hr.bertlab.saga.messaging.events.ShippingScheduled;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class OrderSagaHandlers {

    @Bean
    SagaHandlers orderHandlers(OrderService orders) {
        return SagaHandlers.of(SagaService.ORDER)
                .on(ShippingScheduled.class, event -> orders.confirm(event.orderId()))
                .on(ShippingFailed.class, event -> orders.fail(event.orderId(), "Shipping failed: " + event.reason()))
                .on(
                        InventoryReservationFailed.class,
                        event -> orders.fail(event.orderId(), "Inventory reservation failed: " + event.reason()));
    }
}
