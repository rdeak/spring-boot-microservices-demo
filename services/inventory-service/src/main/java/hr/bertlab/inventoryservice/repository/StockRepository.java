package hr.bertlab.inventoryservice.repository;

import hr.bertlab.inventoryservice.model.Stock;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockRepository extends JpaRepository<Stock, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Stock s where s.sku in :skus order by s.sku")
    List<Stock> lockAll(@Param("skus") Collection<String> skus);
}
