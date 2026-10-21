package hr.bertlab.shippingservice.messaging;

import static hr.bertlab.saga.messaging.test.SagaTestSupport.allProcessed;
import static hr.bertlab.saga.messaging.test.SagaTestSupport.outboxEventTypes;
import static hr.bertlab.saga.messaging.test.SagaTestSupport.publish;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hr.bertlab.saga.messaging.events.InventoryReserved;
import hr.bertlab.saga.messaging.events.OrderItem;
import hr.bertlab.saga.messaging.events.SagaEventCodec;
import hr.bertlab.saga.messaging.topology.SagaNames;
import hr.bertlab.shippingservice.AbstractShippingServiceIT;
import hr.bertlab.shippingservice.model.Shipment;
import hr.bertlab.shippingservice.model.ShipmentStatus;
import hr.bertlab.shippingservice.repository.ShipmentRepository;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

class ShippingSagaIT extends AbstractShippingServiceIT {

    @Autowired
    RabbitTemplate rabbit;

    @Autowired
    SagaNames names;

    @Autowired
    SagaEventCodec codec;

    @Autowired
    ShipmentRepository shipments;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    MockMvc mvc;

    @Test
    void schedulesWithTheFastestCarrierForTheCountry() {
        UUID orderId = UUID.randomUUID();
        publish(rabbit, names, codec, reserved(orderId, "HR"));

        Shipment shipment = awaitShipment(orderId);
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.SCHEDULED);
        assertThat(shipment.getCarrier()).isEqualTo("GLS Croatia");
        assertThat(shipment.getDeliveryDate())
                .isEqualTo(LocalDate.now(ZoneOffset.UTC).plusDays(2));
        assertThat(outboxEventTypes(jdbc, orderId)).containsExactly("ShippingScheduled");
    }

    @Test
    void countryIsMatchedCaseAndWhitespaceInsensitively() {
        UUID orderId = UUID.randomUUID();
        publish(rabbit, names, codec, reserved(orderId, " de "));

        Shipment shipment = awaitShipment(orderId);
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.SCHEDULED);
        assertThat(shipment.getCarrier()).isEqualTo("DHL");
    }

    @Test
    void noCarrierPublishesShippingFailed() {
        UUID orderId = UUID.randomUUID();
        publish(rabbit, names, codec, reserved(orderId, "AQ"));

        Shipment shipment = awaitShipment(orderId);
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.FAILED);
        assertThat(shipment.getReason()).isEqualTo("No carrier available for country 'AQ'");
        assertThat(outboxEventTypes(jdbc, orderId)).containsExactly("ShippingFailed");
    }

    @Test
    void secondInventoryReservedForTheSameOrderIsIgnored() {
        UUID orderId = UUID.randomUUID();

        UUID first = publish(rabbit, names, codec, reserved(orderId, "HR"));
        UUID second = publish(rabbit, names, codec, reserved(orderId, "HR"));

        await().atMost(Duration.ofSeconds(10)).until(() -> allProcessed(jdbc, first, second));
        assertThat(outboxEventTypes(jdbc, orderId)).containsExactly("ShippingScheduled");
    }

    @Test
    void shipmentEndpoint() throws Exception {
        UUID orderId = UUID.randomUUID();
        publish(rabbit, names, codec, reserved(orderId, "US"));
        awaitShipment(orderId);

        mvc.perform(get("/api/shipping/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.carrier").value("UPS"));
        mvc.perform(get("/api/shipping/{orderId}", UUID.randomUUID())).andExpect(status().isNotFound());
    }

    private Shipment awaitShipment(UUID orderId) {
        await().atMost(Duration.ofSeconds(10)).until(() -> shipments.existsById(orderId));
        return shipments.findById(orderId).orElseThrow();
    }

    private static InventoryReserved reserved(UUID orderId, String country) {
        return new InventoryReserved(orderId, List.of(new OrderItem("SKU-1", 1)), country);
    }
}
