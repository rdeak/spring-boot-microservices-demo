package hr.bertlab.orderservice.controller;

import static hr.bertlab.saga.messaging.test.SagaTestSupport.outboxEventTypes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import hr.bertlab.orderservice.AbstractOrderServiceIT;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class OrderApiIT extends AbstractOrderServiceIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Test
    void createsAPendingOrderAndAppendsOrderCreatedInTheSameTransaction() throws Exception {
        MvcResult result = mvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"country\":\" hr \",\"items\":[{\"sku\":\"SKU-1\",\"quantity\":2}]}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.country").value("HR"))
                .andReturn();
        UUID id = UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));

        assertThat(outboxEventTypes(jdbc, id)).containsExactly("OrderCreated");
        String payload = jdbc.sql("select payload::text from outbox_event where aggregate_id = :id")
                .param("id", id)
                .query(String.class)
                .single();
        assertThat(payload).contains("\"country\": \"HR\"").contains("\"sku\": \"SKU-1\"");

        mvc.perform(get("/api/orders/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(2));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"country\":\"HR\",\"items\":[]}",
                "{\"country\":\"HR\"}",
                "{\"country\":\"HR\",\"items\":[{\"sku\":\"SKU-1\",\"quantity\":0}]}",
                "{\"country\":\"HR\",\"items\":[{\"sku\":\"SKU-1\",\"quantity\":-3}]}",
                "{\"country\":\"HR\",\"items\":[{\"sku\":\"SKU-1\"}]}",
                "{\"country\":\"HR\",\"items\":[{\"sku\":\" \",\"quantity\":1}]}",
                "{\"country\":\"HRV\",\"items\":[{\"sku\":\"SKU-1\",\"quantity\":1}]}",
                "{\"items\":[{\"sku\":\"SKU-1\",\"quantity\":1}]}",
                "not json"
            })
    void rejectsMalformedRequestsWithoutWritingAnything(String body) throws Exception {
        long ordersBefore =
                jdbc.sql("select count(*) from orders").query(Long.class).single();
        long outboxBefore =
                jdbc.sql("select count(*) from outbox_event").query(Long.class).single();

        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());

        assertThat(jdbc.sql("select count(*) from orders").query(Long.class).single())
                .isEqualTo(ordersBefore);
        assertThat(jdbc.sql("select count(*) from outbox_event")
                        .query(Long.class)
                        .single())
                .isEqualTo(outboxBefore);
    }

    @Test
    void listsOrdersNewestFirstUpToTheLimit() throws Exception {
        UUID older = create("HR");
        UUID newer = create("DE");

        List<String> ids = JsonPath.read(
                mvc.perform(get("/api/orders").param("limit", "200"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$[*].id");
        assertThat(ids).contains(newer.toString(), older.toString());
        assertThat(ids.indexOf(newer.toString())).isLessThan(ids.indexOf(older.toString()));

        mvc.perform(get("/api/orders").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(newer.toString()))
                .andExpect(jsonPath("$[0].country").value("DE"))
                .andExpect(jsonPath("$[0].items[0].sku").value("SKU-1"));
    }

    @Test
    void listDefaultsToFiftyAndClampsOutOfRangeLimits() throws Exception {
        create("HR");
        mvc.perform(get("/api/orders")).andExpect(status().isOk());
        mvc.perform(get("/api/orders").param("limit", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/orders").param("limit", "100000")).andExpect(status().isOk());
    }

    private UUID create(String country) throws Exception {
        MvcResult result = mvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"country\":\"" + country + "\",\"items\":[{\"sku\":\"SKU-1\",\"quantity\":1}]}"))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    @Test
    void unknownOrderIs404() throws Exception {
        mvc.perform(get("/api/orders/{id}", UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void dlqReplayEndpointIsExposedUnderTheServicePath() throws Exception {
        mvc.perform(post("/api/orders/admin/dlq/replay"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replayed").isNumber());
    }
}
