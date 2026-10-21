package hr.bertlab.orderservice.repository;

import hr.bertlab.orderservice.model.Order;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    List<Order> findAllByOrderByCreatedAtDesc(Limit limit);

    @Query("select o from CustomerOrder o where o.status = hr.bertlab.orderservice.model.OrderStatus.PENDING "
            + "and o.createdAt < :cutoff and o.alertedStuckAt is null order by o.createdAt")
    List<Order> findStuckToAlert(@Param("cutoff") Instant cutoff, Limit limit);

    @Modifying
    @Query("update CustomerOrder o set o.alertedStuckAt = :now where o.id = :id "
            + "and o.status = hr.bertlab.orderservice.model.OrderStatus.PENDING and o.alertedStuckAt is null")
    int claimStuckAlert(@Param("id") UUID id, @Param("now") Instant now);
}
