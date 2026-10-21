package hr.bertlab.orderservice.service;

import hr.bertlab.orderservice.config.StuckSagaProperties;
import hr.bertlab.orderservice.model.Order;
import hr.bertlab.orderservice.repository.OrderRepository;
import hr.bertlab.saga.messaging.ops.AlertCode;
import hr.bertlab.saga.messaging.ops.AlertService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class StuckOrderDetector {

    private static final int MAX_ALERTS_PER_RUN = 100;

    private final OrderRepository orders;
    private final AlertService alerts;
    private final StuckSagaProperties properties;
    private final TransactionTemplate transactions;

    public StuckOrderDetector(
            OrderRepository orders,
            AlertService alerts,
            StuckSagaProperties properties,
            TransactionTemplate sagaTransactionTemplate) {
        this.orders = orders;
        this.alerts = alerts;
        this.properties = properties;
        this.transactions = sagaTransactionTemplate;
    }

    public int detect() {
        Instant now = Instant.now();
        List<Order> stuck = orders.findStuckToAlert(now.minus(properties.threshold()), Limit.of(MAX_ALERTS_PER_RUN));
        int raised = 0;
        for (Order order : stuck) {
            Integer claimed = transactions.execute(status -> orders.claimStuckAlert(order.getId(), now));
            if (claimed != null && claimed == 1) {
                alerts.raise(
                        AlertCode.SAGA_STUCK,
                        Map.of(
                                "orderId", order.getId(),
                                "createdAt", order.getCreatedAt(),
                                "thresholdSeconds", properties.threshold().toSeconds()));
                raised++;
            }
        }
        return raised;
    }
}
