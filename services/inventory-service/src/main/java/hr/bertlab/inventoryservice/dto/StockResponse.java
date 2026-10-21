package hr.bertlab.inventoryservice.dto;

import hr.bertlab.inventoryservice.model.Stock;

public record StockResponse(String sku, int available, int reserved) {

    public static StockResponse from(Stock stock) {
        return new StockResponse(stock.getSku(), stock.getAvailable(), stock.getReserved());
    }
}
