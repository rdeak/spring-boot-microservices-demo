package hr.bertlab.shippingservice.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "carrier")
public class Carrier {

    @Id
    private Long id;

    private String country;

    private String name;

    private int leadDays;

    protected Carrier() {}

    public String getName() {
        return name;
    }

    public int getLeadDays() {
        return leadDays;
    }
}
