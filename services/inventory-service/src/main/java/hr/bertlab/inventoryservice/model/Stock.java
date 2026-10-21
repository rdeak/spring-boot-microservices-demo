package hr.bertlab.inventoryservice.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "stock")
public class Stock {

    @Id
    private String sku;

    private int available;

    private int reserved;

    @Version
    private long version;

    protected Stock() {}

    public Stock(String sku, int available) {
        this.sku = sku;
        this.available = available;
    }

    public boolean canReserve(int quantity) {
        return quantity <= available;
    }

    public void reserve(int quantity) {
        if (!canReserve(quantity)) {
            throw new IllegalStateException(
                    "Cannot reserve " + quantity + " of " + sku + ", only " + available + " available");
        }
        available -= quantity;
        reserved += quantity;
    }

    public void release(int quantity) {
        if (quantity > reserved) {
            throw new IllegalStateException(
                    "Cannot release " + quantity + " of " + sku + ", only " + reserved + " reserved");
        }
        reserved -= quantity;
        available += quantity;
    }

    public String getSku() {
        return sku;
    }

    public int getAvailable() {
        return available;
    }

    public int getReserved() {
        return reserved;
    }
}
