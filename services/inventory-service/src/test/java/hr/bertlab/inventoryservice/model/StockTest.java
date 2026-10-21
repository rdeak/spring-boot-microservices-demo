package hr.bertlab.inventoryservice.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class StockTest {

    @Test
    void reserveMovesAvailableToReservedAndReleaseMovesItBack() {
        Stock stock = new Stock("SKU-1", 10);
        stock.reserve(4);
        assertThat(stock.getAvailable()).isEqualTo(6);
        assertThat(stock.getReserved()).isEqualTo(4);
        stock.release(4);
        assertThat(stock.getAvailable()).isEqualTo(10);
        assertThat(stock.getReserved()).isZero();
    }

    @Test
    void cannotReserveMoreThanAvailableOrReleaseMoreThanReserved() {
        Stock stock = new Stock("SKU-1", 1);
        assertThat(stock.canReserve(2)).isFalse();
        assertThatThrownBy(() -> stock.reserve(2)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> stock.release(1)).isInstanceOf(IllegalStateException.class);
    }
}
