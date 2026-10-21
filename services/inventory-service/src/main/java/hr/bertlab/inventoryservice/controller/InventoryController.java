package hr.bertlab.inventoryservice.controller;

import hr.bertlab.inventoryservice.dto.ReservationResponse;
import hr.bertlab.inventoryservice.dto.StockResponse;
import hr.bertlab.inventoryservice.service.InventoryService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventory;

    public InventoryController(InventoryService inventory) {
        this.inventory = inventory;
    }

    @GetMapping
    public List<StockResponse> listStock() {
        return inventory.listStock().stream().map(StockResponse::from).toList();
    }

    @GetMapping("/reservations")
    public List<ReservationResponse> listReservations(@RequestParam(defaultValue = "50") int limit) {
        return inventory.listReservations(limit).stream()
                .map(ReservationResponse::from)
                .toList();
    }

    @GetMapping("/{sku}")
    public ResponseEntity<StockResponse> get(@PathVariable String sku) {
        return ResponseEntity.of(inventory.findStock(sku).map(StockResponse::from));
    }
}
