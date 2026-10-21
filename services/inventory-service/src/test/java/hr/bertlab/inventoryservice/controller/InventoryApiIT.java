package hr.bertlab.inventoryservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import hr.bertlab.inventoryservice.AbstractInventoryServiceIT;
import hr.bertlab.inventoryservice.service.InventoryService;
import hr.bertlab.saga.messaging.events.OrderCreated;
import hr.bertlab.saga.messaging.events.OrderItem;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

class InventoryApiIT extends AbstractInventoryServiceIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    InventoryService inventory;

    @Test
    void listsAllStockSortedBySku() throws Exception {
        String body = mvc.perform(get("/api/inventory"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].available").isNumber())
                .andExpect(jsonPath("$[0].reserved").isNumber())
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<String> skus = JsonPath.read(body, "$[*].sku");
        assertThat(skus).contains("SKU-1", "SKU-2", "SKU-LOW").isSorted().doesNotHaveDuplicates();
    }

    @Test
    void singleSkuIsStillServedAndUnknownSkuIs404() throws Exception {
        mvc.perform(get("/api/inventory/{sku}", "SKU-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("SKU-2"));
        mvc.perform(get("/api/inventory/{sku}", "NO-SUCH")).andExpect(status().isNotFound());
    }

    @Test
    void listsReservationsNewestFirstWithItemsAndReason() throws Exception {
        UUID reserved = UUID.randomUUID();
        UUID rejected = UUID.randomUUID();
        inventory.reserve(new OrderCreated(reserved, List.of(new OrderItem("SKU-2", 3)), "HR"));
        inventory.reserve(new OrderCreated(rejected, List.of(new OrderItem("SKU-LOW", 99)), "HR"));

        String body = mvc.perform(get("/api/inventory/reservations").param("limit", "200"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<String> ids = JsonPath.read(body, "$[*].orderId");
        assertThat(ids.indexOf(rejected.toString())).isLessThan(ids.indexOf(reserved.toString()));

        String reservedPath = "$[?(@.orderId=='" + reserved + "')]";
        String rejectedPath = "$[?(@.orderId=='" + rejected + "')]";
        mvc.perform(get("/api/inventory/reservations").param("limit", "200"))
                .andExpect(jsonPath(reservedPath + ".status").value("RESERVED"))
                .andExpect(jsonPath(reservedPath + ".items[0].sku").value("SKU-2"))
                .andExpect(jsonPath(reservedPath + ".items[0].quantity").value(3))
                .andExpect(jsonPath(reservedPath + ".createdAt").exists())
                .andExpect(jsonPath(rejectedPath + ".status").value("REJECTED"))
                .andExpect(jsonPath(rejectedPath + ".reason")
                        .value("Insufficient stock for SKU-LOW: requested 99, available 1"))
                .andExpect(jsonPath(rejectedPath + ".items[0]").doesNotExist());

        mvc.perform(get("/api/inventory/reservations").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].orderId").value(rejected.toString()));
    }
}
