package hr.bertlab.saga.messaging.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;

class SagaMessageTest {

    @Test
    void headerNamesAreTheWireContract() {
        assertThat(SagaMessage.EVENT_ID).isEqualTo("eventId");
        assertThat(SagaMessage.EVENT_TYPE).isEqualTo("eventType");
        assertThat(SagaMessage.ORDER_ID).isEqualTo("orderId");
        assertThat(SagaMessage.OCCURRED_AT).isEqualTo("occurredAt");
    }

    @Test
    void buildsAPersistentJsonMessageWithAllSagaHeaders() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-10-10T10:00:00Z");

        Message message = SagaMessage.build(eventId, EventType.ORDER_CREATED, orderId, occurredAt, "{\"a\":1}");

        MessageProperties props = message.getMessageProperties();
        assertThat(new String(message.getBody(), StandardCharsets.UTF_8)).isEqualTo("{\"a\":1}");
        assertThat(props.getContentType()).isEqualTo(MessageProperties.CONTENT_TYPE_JSON);
        assertThat(props.getContentEncoding()).isEqualTo("UTF-8");
        assertThat(props.getMessageId()).isEqualTo(eventId.toString());
        assertThat(props.getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat((String) props.getHeader(SagaMessage.EVENT_ID)).isEqualTo(eventId.toString());
        assertThat((String) props.getHeader(SagaMessage.EVENT_TYPE)).isEqualTo("OrderCreated");
        assertThat((String) props.getHeader(SagaMessage.ORDER_ID)).isEqualTo(orderId.toString());
        assertThat((String) props.getHeader(SagaMessage.OCCURRED_AT)).isEqualTo("2026-10-10T10:00:00Z");
    }
}
