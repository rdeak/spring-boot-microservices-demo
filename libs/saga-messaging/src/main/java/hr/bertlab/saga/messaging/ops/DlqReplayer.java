package hr.bertlab.saga.messaging.ops;

import com.rabbitmq.client.GetResponse;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * Moves dead letters back to the service's hash exchange (original headers, so the same partition).
 * Only the messages present when replay starts are moved.
 * Each message is acked from the DLQ only after the broker has confirmed the republish
 * (publisher confirm awaited before the ack). A failed or unconfirmed publish leaves the message
 * unacked, so it is requeued and never lost. A crash between confirm and ack can duplicate;
 * consumers are idempotent.
 */
public class DlqReplayer {

    private final RabbitTemplate rabbit;
    private final String deadLetterQueue;
    private final String targetExchange;

    public DlqReplayer(RabbitTemplate rabbit, String deadLetterQueue, String targetExchange) {
        this.rabbit = rabbit;
        this.deadLetterQueue = deadLetterQueue;
        this.targetExchange = targetExchange;
    }

    /**
     * Replays at most the messages that were in the DLQ when the call started, so a message that
     * fails again and lands back in the DLQ is not replayed in a loop.
     */
    public int replay(int max) {
        Long depth = rabbit.execute(channel -> channel.messageCount(deadLetterQueue));
        long limit = Math.min(max, depth == null ? 0 : depth);
        int replayed = 0;
        while (replayed < limit) {
            Boolean moved = rabbit.execute(channel -> {
                GetResponse response = channel.basicGet(deadLetterQueue, false);
                if (response == null) {
                    return false;
                }
                channel.basicPublish(targetExchange, "", response.getProps(), response.getBody());
                channel.waitForConfirmsOrDie(5000);
                channel.basicAck(response.getEnvelope().getDeliveryTag(), false);
                return true;
            });
            if (!Boolean.TRUE.equals(moved)) {
                break;
            }
            replayed++;
        }
        return replayed;
    }
}
