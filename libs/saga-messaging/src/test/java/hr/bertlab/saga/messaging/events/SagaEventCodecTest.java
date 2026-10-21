package hr.bertlab.saga.messaging.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class SagaEventCodecTest {

    private final SagaEventCodec codec = new SagaEventCodec(JsonMapper.builder().build());

    @Test
    void roundTripsEveryEventType() {
        UUID orderId = UUID.randomUUID();
        List<SagaEvent> events = List.of(
                new OrderCreated(orderId, List.of(new OrderItem("SKU-1", 2)), "HR"),
                new InventoryReserved(orderId, List.of(new OrderItem("SKU-1", 2)), "HR"),
                new InventoryReservationFailed(orderId, "Insufficient stock"),
                new ShippingScheduled(orderId, "DHL", LocalDate.of(2026, 10, 12)),
                new ShippingFailed(orderId, "No carrier"));

        for (SagaEvent event : events) {
            byte[] json = codec.encode(event).getBytes(StandardCharsets.UTF_8);
            assertThat(codec.decode(EventType.of(event), json)).isEqualTo(event);
        }
    }

    @Test
    void malformedJsonFailsDecoding() {
        assertThatThrownBy(() -> codec.decode(EventType.ORDER_CREATED, "not json".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(EventDecodingException.class)
                .hasMessageContaining("OrderCreated");
    }

    @Test
    void missingOrderIdFailsDecoding() {
        assertThatThrownBy(() ->
                        codec.decode(EventType.SHIPPING_FAILED, "{\"reason\":\"x\"}".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(EventDecodingException.class)
                .hasMessageContaining("orderId");
    }
}
