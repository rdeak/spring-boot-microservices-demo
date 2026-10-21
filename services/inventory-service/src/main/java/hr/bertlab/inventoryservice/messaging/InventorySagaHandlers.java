package hr.bertlab.inventoryservice.messaging;

import hr.bertlab.inventoryservice.service.InventoryService;
import hr.bertlab.saga.messaging.SagaService;
import hr.bertlab.saga.messaging.consume.SagaHandlers;
import hr.bertlab.saga.messaging.events.OrderCreated;
import hr.bertlab.saga.messaging.events.ShippingFailed;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class InventorySagaHandlers {

    @Bean
    SagaHandlers inventoryHandlers(InventoryService inventory) {
        return SagaHandlers.of(SagaService.INVENTORY)
                .on(OrderCreated.class, inventory::reserve)
                .on(ShippingFailed.class, inventory::release);
    }
}
