package hr.bertlab.saga.messaging.publish;

import hr.bertlab.saga.messaging.events.EventType;
import hr.bertlab.saga.messaging.events.SagaEvent;
import hr.bertlab.saga.messaging.events.SagaEventCodec;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

public class Outbox {

    private static final Logger log = LoggerFactory.getLogger(Outbox.class);
    private static final TypeReference<Map<String, String>> TRACE_CONTEXT = new TypeReference<>() {};

    private final JdbcClient jdbc;
    private final SagaEventCodec codec;
    private final OutboxTraceContext tracing;
    private final JsonMapper jsonMapper;

    public Outbox(JdbcClient jdbc, SagaEventCodec codec, OutboxTraceContext tracing, JsonMapper jsonMapper) {
        this.jdbc = jdbc;
        this.codec = codec;
        this.tracing = tracing;
        this.jsonMapper = jsonMapper;
    }

    public UUID append(SagaEvent event) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalTransactionStateException("Outbox.append must run inside the business transaction");
        }
        UUID eventId = UUID.randomUUID();
        jdbc.sql("""
                insert into outbox_event (event_id, aggregate_id, event_type, payload, trace_context)
                values (:eventId, :aggregateId, :eventType, cast(:payload as jsonb), cast(:traceContext as jsonb))""")
                .param("eventId", eventId)
                .param("aggregateId", event.orderId())
                .param("eventType", EventType.of(event).wireName())
                .param("payload", codec.encode(event))
                .param("traceContext", jsonMapper.writeValueAsString(tracing.capture()))
                .update();
        return eventId;
    }

    public boolean tryLock(String key) {
        return Boolean.TRUE.equals(jdbc.sql("select pg_try_advisory_xact_lock(hashtext(:key))")
                .param("key", key)
                .query(Boolean.class)
                .single());
    }

    public List<OutboxRow> fetchPending(int limit) {
        return jdbc.sql("""
                select id, event_id, aggregate_id, event_type, payload::text as payload, created_at,
                       trace_context::text as trace_context
                from outbox_event
                where published_at is null
                order by id
                limit :limit""")
                .param("limit", limit)
                .query((rs, rowNum) -> new OutboxRow(
                        rs.getLong("id"),
                        rs.getObject("event_id", UUID.class),
                        rs.getObject("aggregate_id", UUID.class),
                        rs.getString("event_type"),
                        rs.getString("payload"),
                        rs.getTimestamp("created_at").toInstant(),
                        decodeTraceContext(rs.getLong("id"), rs.getString("trace_context"))))
                .list();
    }

    public void markPublished(long id) {
        jdbc.sql("update outbox_event set published_at = now() where id = :id")
                .param("id", id)
                .update();
    }

    public int purgePublishedOlderThan(Duration retention) {
        return jdbc.sql("delete from outbox_event where published_at < now() - make_interval(secs => :seconds)")
                .param("seconds", (double) retention.toSeconds())
                .update();
    }

    public OutboxStats stats() {
        return jdbc.sql("""
                select count(*) as pending,
                       coalesce(extract(epoch from now() - min(created_at)), 0) as age_seconds
                from outbox_event
                where published_at is null""")
                .query((rs, rowNum) -> new OutboxStats(
                        rs.getLong("pending"), Duration.ofMillis(Math.round(rs.getDouble("age_seconds") * 1000))))
                .single();
    }

    private Map<String, String> decodeTraceContext(long id, @Nullable String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, String> context = jsonMapper.readValue(json, TRACE_CONTEXT);
            return context == null ? Map.of() : context;
        } catch (JacksonException ex) {
            log.warn("Outbox row {} has an unreadable trace_context, publishing it as a new trace", id);
            return Map.of();
        }
    }
}
