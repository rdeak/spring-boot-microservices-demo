package hr.bertlab.saga.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;

class SchemaIsolationIT extends AbstractSagaMessagingIT {

    @Autowired
    JdbcClient jdbc;

    @Value("${app.schema}")
    String schema;

    @Test
    void connectionsUseTheConfiguredSchemaAndSharedTablesExist() {
        assertThat(schema).startsWith("saga_lib_t");
        assertThat(jdbc.sql("select current_schema()").query(String.class).single())
                .isEqualTo(schema);
        assertThat(jdbc.sql("""
                select count(*) from information_schema.tables
                where table_schema = :schema and table_name in ('outbox_event', 'processed_message')""").param("schema", schema).query(Long.class).single())
                .isEqualTo(2L);
    }
}
