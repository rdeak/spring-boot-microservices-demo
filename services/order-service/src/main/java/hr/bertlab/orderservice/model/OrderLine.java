package hr.bertlab.orderservice.model;

import jakarta.persistence.Embeddable;

@Embeddable
public record OrderLine(String sku, int quantity) {}
