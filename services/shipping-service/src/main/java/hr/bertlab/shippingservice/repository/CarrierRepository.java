package hr.bertlab.shippingservice.repository;

import hr.bertlab.shippingservice.model.Carrier;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CarrierRepository extends JpaRepository<Carrier, Long> {

    Optional<Carrier> findFirstByCountryOrderByLeadDaysAscNameAsc(String country);
}
