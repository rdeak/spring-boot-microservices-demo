package hr.bertlab.inventoryservice.model;

import jakarta.persistence.Embeddable;

@Embeddable
public record ReservationLine(String sku, int quantity) {}
