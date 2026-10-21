package hr.bertlab.orderservice.controller;

import hr.bertlab.orderservice.dto.CreateOrderRequest;
import hr.bertlab.orderservice.dto.OrderResponse;
import hr.bertlab.orderservice.model.InvalidOrderException;
import hr.bertlab.orderservice.model.Order;
import hr.bertlab.orderservice.service.OrderService;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(@RequestBody CreateOrderRequest request) {
        Order order = orders.place(request.country(), request.toLines());
        return ResponseEntity.created(URI.create("/api/orders/" + order.getId()))
                .body(OrderResponse.from(order));
    }

    @GetMapping
    public List<OrderResponse> list(@RequestParam(defaultValue = "50") int limit) {
        return orders.list(limit).stream().map(OrderResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> get(@PathVariable UUID id) {
        return ResponseEntity.of(orders.find(id).map(OrderResponse::from));
    }

    @ExceptionHandler(InvalidOrderException.class)
    ProblemDetail invalidRequest(InvalidOrderException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
}
