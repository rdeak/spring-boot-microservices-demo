package hr.bertlab.saga.messaging.consume;

import hr.bertlab.saga.messaging.events.EventType;
import hr.bertlab.saga.messaging.events.SagaMessage;
import hr.bertlab.saga.messaging.ops.AlertCode;
import hr.bertlab.saga.messaging.ops.AlertService;
import hr.bertlab.saga.messaging.ops.SagaMetrics;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.core.NestedExceptionUtils;

/**
 * Runs when retries are exhausted (or immediately for poison messages): records the dead letter,
 * raises an alert, then rejects without requeue so the queue's DLX routes it to the DLQ.
 */
public class SagaDeadLetterRecoverer extends RejectAndDontRequeueRecoverer {

    private final AlertService alerts;
    private final SagaMetrics metrics;

    public SagaDeadLetterRecoverer(AlertService alerts, SagaMetrics metrics) {
        this.alerts = alerts;
        this.metrics = metrics;
    }

    public static boolean isPoison(@Nullable Throwable throwable) {
        for (Throwable t = throwable; t != null; t = t.getCause()) {
            if (t instanceof PoisonMessageException) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void recover(Message message, @Nullable Throwable cause) {
        MessageProperties properties = message.getMessageProperties();
        String queue = String.valueOf(properties.getConsumerQueue());
        Object typeHeader = properties.getHeader(SagaMessage.EVENT_TYPE);
        String eventType = typeHeader == null ? "unknown" : typeHeader.toString();

        AlertCode code;
        if (isPoison(cause)) {
            code = AlertCode.POISON_MESSAGE;
        } else if (EventType.fromWireName(eventType)
                .map(EventType::isCompensation)
                .orElse(false)) {
            code = AlertCode.COMPENSATION_FAILED;
        } else {
            code = AlertCode.MESSAGE_DEAD_LETTERED;
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("queue", queue);
        details.put("eventType", eventType);
        details.put("eventId", properties.getHeader(SagaMessage.EVENT_ID));
        details.put("orderId", properties.getHeader(SagaMessage.ORDER_ID));
        details.put(
                "error",
                cause == null
                        ? "unknown"
                        : NestedExceptionUtils.getMostSpecificCause(cause).toString());

        metrics.deadLettered(queue, eventType);
        metrics.eventProcessed(eventType, SagaMetrics.OUTCOME_FAILED);
        alerts.raise(code, details);
        super.recover(message, cause);
    }
}
