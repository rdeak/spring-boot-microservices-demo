package hr.bertlab.saga.messaging.test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Removes what crashed or killed test runs left behind: their schemas and RabbitMQ objects in the
 * docker-compose environment. Run it with {@code ./gradlew cleanTestLeftovers}. Everything of a run (all
 * names carry its run id) is kept while any of its schemas is younger than {@code staleAfterMinutes}
 * (default 30), so runs that are still going are never touched. Connection settings are resolved like the
 * services do: environment variable, then the repository's {@code .env}, then the docker-compose default.
 */
public final class TestLeftoverCleaner {

    // Gradle runs this with the library's directory as working directory.
    private static final Properties DOT_ENV = loadDotEnv(Path.of("../../.env"));

    private static final String DB_URL = "jdbc:postgresql://" + setting("DB_HOST", "localhost") + ":"
            + setting("DB_PORT", "5432") + "/" + setting("DB_NAME", "micro-services");
    private static final String DB_USER = setting("DB_USER", "dev");
    private static final String DB_PASSWORD = setting("DB_PASSWORD", "dev");
    private static final URI RABBIT_API = URI.create("http://" + setting("RABBITMQ_HOST", "localhost") + ":15672/api/");
    private static final String RABBIT_AUTH = "Basic "
            + Base64.getEncoder()
                    .encodeToString((setting("RABBITMQ_USER", "dev") + ":" + setting("RABBITMQ_PASSWORD", "dev"))
                            .getBytes(StandardCharsets.UTF_8));

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private TestLeftoverCleaner() {}

    public static void main(String[] args) throws Exception {
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(Long.getLong("staleAfterMinutes", 30)));
        Set<String> liveRuns = new HashSet<>();
        int droppedSchemas = 0;

        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                Statement statement = connection.createStatement()) {
            List<Matcher> schemas = new ArrayList<>();
            try (ResultSet rows = statement.executeQuery("select nspname from pg_namespace")) {
                while (rows.next()) {
                    Matcher schema = SagaTestSupport.TEST_SCHEMA.matcher(rows.getString(1));
                    if (schema.matches()) {
                        schemas.add(schema);
                    }
                }
            }
            for (Matcher schema : schemas) {
                Instant createdAt = createdAt(statement, schema.group());
                if (createdAt != null && createdAt.isAfter(cutoff)) {
                    liveRuns.add(schema.group(1));
                }
            }
            for (Matcher schema : schemas) {
                if (!liveRuns.contains(schema.group(1))) {
                    statement.execute("DROP SCHEMA \"" + schema.group() + "\" CASCADE");
                    droppedSchemas++;
                }
            }
        }

        int deletedBrokerObjects = 0;
        for (String kind : List.of("queues", "exchanges")) {
            for (JsonNode object : JsonMapper.shared().readTree(send("GET", kind + "/%2F"))) {
                Matcher name = SagaTestSupport.TEST_BROKER_NAME.matcher(
                        object.path("name").asString());
                if (name.matches() && !liveRuns.contains(name.group(1))) {
                    send("DELETE", kind + "/%2F/" + URLEncoder.encode(name.group(), StandardCharsets.UTF_8));
                    deletedBrokerObjects++;
                }
            }
        }
        System.out.println("Dropped " + droppedSchemas + " schemas and " + deletedBrokerObjects
                + " queues/exchanges; kept " + liveRuns.size() + " runs that may still be going");
    }

    private static String setting(String name, String defaultValue) {
        String value = System.getenv(name);
        return value != null ? value : DOT_ENV.getProperty(name, defaultValue);
    }

    private static Properties loadDotEnv(Path file) {
        Properties properties = new Properties();
        if (Files.isReadable(file)) {
            try (BufferedReader reader = Files.newBufferedReader(file)) {
                properties.load(reader);
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }
        return properties;
    }

    /** When Flyway first migrated the schema, or null if it has no history (treated as stale). */
    private static @Nullable Instant createdAt(Statement statement, String schema) {
        try (ResultSet rows =
                statement.executeQuery("select min(installed_on) from \"" + schema + "\".flyway_schema_history")) {
            Timestamp installedOn = rows.next() ? rows.getTimestamp(1) : null;
            return installedOn == null ? null : installedOn.toInstant();
        } catch (SQLException ex) {
            return null;
        }
    }

    private static String send(String method, String path) throws Exception {
        HttpResponse<String> response = HTTP.send(
                HttpRequest.newBuilder(RABBIT_API.resolve(path))
                        .method(method, HttpRequest.BodyPublishers.noBody())
                        .header("Authorization", RABBIT_AUTH)
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            throw new IllegalStateException(method + " " + path + " returned " + response.statusCode());
        }
        return response.body();
    }
}
