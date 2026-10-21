package hr.bertlab.shippingservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import hr.bertlab.saga.messaging.events.InventoryReserved;
import hr.bertlab.saga.messaging.events.OrderItem;
import hr.bertlab.shippingservice.AbstractShippingServiceIT;
import hr.bertlab.shippingservice.service.ShippingService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

class ShippingApiIT extends AbstractShippingServiceIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    ShippingService shipping;

    @Test
    void listsShipmentsNewestFirstUpToTheLimit() throws Exception {
        UUID scheduled = UUID.randomUUID();
        UUID failed = UUID.randomUUID();
        shipping.schedule(new InventoryReserved(scheduled, List.of(new OrderItem("SKU-1", 1)), "HR"));
        shipping.schedule(new InventoryReserved(failed, List.of(new OrderItem("SKU-1", 1)), "AQ"));

        String body = mvc.perform(get("/api/shipping").param("limit", "200"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<String> ids = JsonPath.read(body, "$[*].orderId");
        assertThat(ids.indexOf(failed.toString())).isLessThan(ids.indexOf(scheduled.toString()));

        String scheduledPath = "$[?(@.orderId=='" + scheduled + "')]";
        String failedPath = "$[?(@.orderId=='" + failed + "')]";
        mvc.perform(get("/api/shipping").param("limit", "200"))
                .andExpect(jsonPath(scheduledPath + ".status").value("SCHEDULED"))
                .andExpect(jsonPath(scheduledPath + ".carrier").value("GLS Croatia"))
                .andExpect(jsonPath(scheduledPath + ".deliveryDate").exists())
                .andExpect(jsonPath(scheduledPath + ".createdAt").exists())
                .andExpect(jsonPath(failedPath + ".status").value("FAILED"))
                .andExpect(jsonPath(failedPath + ".reason").value("No carrier available for country 'AQ'"));

        mvc.perform(get("/api/shipping").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].orderId").value(failed.toString()));
    }

    @Test
    void singleShipmentIncludesCreatedAtAndUnknownOrderIs404() throws Exception {
        UUID orderId = UUID.randomUUID();
        shipping.schedule(new InventoryReserved(orderId, List.of(new OrderItem("SKU-1", 1)), "DE"));

        mvc.perform(get("/api/shipping/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.carrier").value("DHL"))
                .andExpect(jsonPath("$.createdAt").exists());
        mvc.perform(get("/api/shipping/{orderId}", UUID.randomUUID())).andExpect(status().isNotFound());
    }
}
