package hr.bertlab.saga.messaging.events;

/**
 * A payload that is not a valid instance of its declared event type (malformed JSON, missing orderId).
 * Transport-agnostic: the consumer decides how to treat it.
 */
public class EventDecodingException extends RuntimeException {

    public EventDecodingException(String message) {
        super(message);
    }

    public EventDecodingException(String message, Throwable cause) {
        super(message, cause);
    }
}
