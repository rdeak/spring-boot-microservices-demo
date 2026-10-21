package hr.bertlab.sagae2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import hr.bertlab.inventoryservice.InventoryServiceApplication;
import hr.bertlab.orderservice.OrderServiceApplication;
import hr.bertlab.saga.messaging.test.SagaTestSupport;
import hr.bertlab.shippingservice.ShippingServiceApplication;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Runs all three services in one JVM against the docker-compose infrastructure. Each app loads its
 * own application.yaml from disk (the classpath copies would collide) and gets its own fresh schema.
 * All three share one topology prefix so they talk to each other and to nobody else.
 */
class SagaEndToEndTest {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OrderView(UUID id, String status, String failureReason) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record StockView(String sku, int available, int reserved) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ShipmentView(UUID orderId, String status, String carrier, String reason) {}

    private static ConfigurableApplicationContext inventory;
    private static ConfigurableApplicationContext shipping;
    private static ConfigurableApplicationContext order;
    private static RestClient orderApi;
    private static RestClient inventoryApi;
    private static RestClient shippingApi;

    @BeforeAll
    static void startServices() {
        String runId = SagaTestSupport.newRunId();
        inventory = start(InventoryServiceApplication.class, "inventory-service", "inventory", runId);
        shipping = start(ShippingServiceApplication.class, "shipping-service", "shipping", runId);
        order = start(OrderServiceApplication.class, "order-service", "orders", runId);
        orderApi = client(order);
        inventoryApi = client(inventory);
        shippingApi = client(shipping);
    }

    @AfterAll
    static void stopServices() {
        for (ConfigurableApplicationContext context :
                new ConfigurableApplicationContext[] {order, shipping, inventory}) {
            if (context != null) {
                context.close();
            }
        }
    }

    @Test
    void happyPathConfirmsTheOrderAndKeepsStockReserved() {
        StockView before = stock("SKU-1");

        UUID id = placeOrder("HR", "SKU-1", 2);

        OrderView placed = awaitTerminal(id);
        assertThat(placed.status()).isEqualTo("CONFIRMED");
        StockView after = stock("SKU-1");
        assertThat(after.available()).isEqualTo(before.available() - 2);
        assertThat(after.reserved()).isEqualTo(before.reserved() + 2);
        ShipmentView shipment =
                shippingApi.get().uri("/api/shipping/{id}", id).retrieve().body(ShipmentView.class);
        assertThat(shipment).isNotNull();
        assertThat(shipment.status()).isEqualTo("SCHEDULED");
        assertThat(shipment.carrier()).isEqualTo("GLS Croatia");
    }

    @Test
    void noCarrierCompensatesStockAndFailsTheOrder() {
        StockView before = stock("SKU-1");

        UUID id = placeOrder("AQ", "SKU-1", 3);

        OrderView failed = awaitTerminal(id);
        assertThat(failed.status()).isEqualTo("FAILED");
        assertThat(failed.failureReason()).contains("No carrier available for country 'AQ'");
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            StockView after = stock("SKU-1");
            assertThat(after.available()).isEqualTo(before.available());
            assertThat(after.reserved()).isEqualTo(before.reserved());
        });
    }

    @Test
    void insufficientStockFailsTheOrderWithoutTouchingStock() {
        StockView before = stock("SKU-LOW");

        UUID id = placeOrder("HR", "SKU-LOW", 5);

        OrderView failed = awaitTerminal(id);
        assertThat(failed.status()).isEqualTo("FAILED");
        assertThat(failed.failureReason()).contains("Insufficient stock for SKU-LOW");
        assertThat(stock("SKU-LOW")).isEqualTo(before);
    }

    @Test
    void sagaMetricsAreExposedViaActuator() {
        placeOrder("DE", "SKU-2", 1);
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            String metrics =
                    inventoryApi.get().uri("/actuator/metrics").retrieve().body(String.class);
            assertThat(metrics).contains("saga.events.processed").contains("saga.dlq.depth");
        });
    }

    private static UUID placeOrder(String country, String sku, int quantity) {
        Map<String, Object> request =
                Map.of("country", country, "items", List.of(Map.of("sku", sku, "quantity", quantity)));
        OrderView created = orderApi.post()
                .uri("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(OrderView.class);
        assertThat(created).isNotNull();
        assertThat(created.status()).isEqualTo("PENDING");
        return created.id();
    }

    private static OrderView awaitTerminal(UUID id) {
        return await().atMost(Duration.ofSeconds(30))
                .until(
                        () -> orderApi.get()
                                .uri("/api/orders/{id}", id)
                                .retrieve()
                                .body(OrderView.class),
                        view -> !"PENDING".equals(view.status()));
    }

    private static StockView stock(String sku) {
        StockView stock =
                inventoryApi.get().uri("/api/inventory/{sku}", sku).retrieve().body(StockView.class);
        assertThat(stock).isNotNull();
        return stock;
    }

    private static ConfigurableApplicationContext start(
            Class<?> application, String module, String schemaBase, String runId) {
        List<String> args = new ArrayList<>(List.of(
                "--spring.config.location=file:../../services/" + module + "/src/main/resources/application.yaml",
                "--server.port=0",
                "--app.schema=" + SagaTestSupport.schemaFor(schemaBase, runId),
                "--saga.messaging.prefix=" + SagaTestSupport.prefixFor(runId)));
        SagaTestSupport.FAST_TIMERS.forEach((key, value) -> args.add("--" + key + "=" + value));
        return new SpringApplicationBuilder(application).run(args.toArray(String[]::new));
    }

    private static RestClient client(ConfigurableApplicationContext context) {
        Integer port = context.getEnvironment().getProperty("local.server.port", Integer.class);
        return RestClient.create("http://localhost:" + port);
    }
}
