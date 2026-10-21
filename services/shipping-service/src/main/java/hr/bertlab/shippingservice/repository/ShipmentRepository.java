package hr.bertlab.shippingservice.repository;

import hr.bertlab.shippingservice.model.Shipment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShipmentRepository extends JpaRepository<Shipment, UUID> {

    List<Shipment> findAllByOrderByCreatedAtDesc(Limit limit);
}
