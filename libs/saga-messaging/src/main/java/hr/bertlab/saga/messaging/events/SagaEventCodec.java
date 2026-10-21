package hr.bertlab.saga.messaging.events;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * JSON (de)serialization of saga payloads. The event type travels in a header, never in the body.
 */
public class SagaEventCodec {

    private final JsonMapper jsonMapper;

    public SagaEventCodec(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public String encode(SagaEvent event) {
        return jsonMapper.writeValueAsString(event);
    }

    public SagaEvent decode(EventType type, byte[] payload) {
        SagaEvent event;
        try {
            event = jsonMapper.readValue(payload, type.payloadType());
        } catch (JacksonException ex) {
            throw new EventDecodingException(
                    "Cannot decode " + type.wireName() + " payload: " + ex.getOriginalMessage(), ex);
        }
        if (event == null || event.orderId() == null) {
            throw new EventDecodingException(type.wireName() + " payload has no orderId");
        }
        return event;
    }
}
