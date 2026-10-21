package hr.bertlab.orderservice.service;

import static hr.bertlab.saga.messaging.test.SagaTestSupport.outboxEventTypes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hr.bertlab.orderservice.AbstractOrderServiceIT;
import hr.bertlab.orderservice.model.OrderLine;
import hr.bertlab.orderservice.repository.OrderRepository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

class OrderOutboxAtomicityIT extends AbstractOrderServiceIT {

    @Autowired
    OrderService orderService;

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TransactionTemplate sagaTransactionTemplate;

    @Test
    void aTransactionThatThrowsAfterPlacingLeavesNoOrderAndNoOutboxRow() {
        AtomicReference<UUID> placed = new AtomicReference<>();

        assertThatThrownBy(() -> sagaTransactionTemplate.executeWithoutResult(status -> {
                    placed.set(orderService
                            .place("HR", List.of(new OrderLine("SKU-1", 1)))
                            .getId());
                    throw new IllegalStateException("boom");
                }))
                .isInstanceOf(IllegalStateException.class);

        UUID id = placed.get();
        assertThat(id).isNotNull();
        assertThat(orderRepository.findById(id)).isEmpty();
        assertThat(outboxEventTypes(jdbc, id)).isEmpty();
    }
}
