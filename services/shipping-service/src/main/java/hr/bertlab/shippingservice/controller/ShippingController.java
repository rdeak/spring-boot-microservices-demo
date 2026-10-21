package hr.bertlab.shippingservice.controller;

import hr.bertlab.shippingservice.dto.ShipmentResponse;
import hr.bertlab.shippingservice.service.ShippingService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shipping")
public class ShippingController {

    private final ShippingService shipping;

    public ShippingController(ShippingService shipping) {
        this.shipping = shipping;
    }

    @GetMapping
    public List<ShipmentResponse> list(@RequestParam(defaultValue = "50") int limit) {
        return shipping.list(limit).stream().map(ShipmentResponse::from).toList();
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<ShipmentResponse> get(@PathVariable UUID orderId) {
        return ResponseEntity.of(shipping.find(orderId).map(ShipmentResponse::from));
    }
}
