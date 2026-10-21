package hr.bertlab.saga.messaging.consume;

import hr.bertlab.saga.messaging.events.EventType;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Deduplicates deliveries. Call inside the handler's transaction so a failed attempt rolls back
 * its record and the retry is not mistaken for a duplicate.
 */
public class Inbox {

    private final JdbcClient jdbc;

    public Inbox(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** @return true if this is the first time the event is seen (row inserted), false for a duplicate */
    public boolean tryRecord(UUID eventId, EventType type) {
        return jdbc.sql("""
                insert into processed_message (event_id, event_type)
                values (:eventId, :eventType)
                on conflict (event_id) do nothing""")
                        .param("eventId", eventId)
                        .param("eventType", type.wireName())
                        .update()
                == 1;
    }
}
