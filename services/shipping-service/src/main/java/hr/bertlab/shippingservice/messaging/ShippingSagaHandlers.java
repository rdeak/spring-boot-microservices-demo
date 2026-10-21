package hr.bertlab.shippingservice.messaging;

import hr.bertlab.saga.messaging.SagaService;
import hr.bertlab.saga.messaging.consume.SagaHandlers;
import hr.bertlab.saga.messaging.events.InventoryReserved;
import hr.bertlab.shippingservice.service.ShippingService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ShippingSagaHandlers {

    @Bean
    SagaHandlers shippingHandlers(ShippingService shipping) {
        return SagaHandlers.of(SagaService.SHIPPING).on(InventoryReserved.class, shipping::schedule);
    }
}
