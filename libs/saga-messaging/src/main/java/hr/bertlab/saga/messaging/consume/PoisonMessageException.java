package hr.bertlab.saga.messaging.consume;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;

/**
 * A message that can never be processed (bad headers, unknown type, undecodable body).
 * It is not retried; it goes straight to the dead-letter queue.
 */
public class PoisonMessageException extends AmqpRejectAndDontRequeueException {

    public PoisonMessageException(String message) {
        super(message);
    }

    public PoisonMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}
