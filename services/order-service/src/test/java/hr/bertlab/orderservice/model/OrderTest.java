package hr.bertlab.orderservice.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OrderTest {

    private static final List<OrderLine> ONE_LINE = List.of(new OrderLine("SKU-1", 2));

    @Test
    void placesAPendingOrderWithNormalizedCountryAndSku() {
        Order order = Order.place(" hr ", List.of(new OrderLine(" SKU-1 ", 2)));
        assertThat(order.getId()).isNotNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getCountry()).isEqualTo("HR");
        assertThat(order.getItems()).containsExactly(new OrderLine("SKU-1", 2));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "H", "HRV", "1A"})
    void rejectsInvalidCountries(String country) {
        assertThatThrownBy(() -> Order.place(country, ONE_LINE)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidItems() {
        assertThatThrownBy(() -> Order.place("HR", List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Order.place("HR", List.of(new OrderLine("SKU-1", 0))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Order.place("HR", List.of(new OrderLine("SKU-1", -1))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Order.place("HR", List.of(new OrderLine(" ", 1))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void confirmAndFailOnlyApplyToPendingOrders() {
        Order confirmed = Order.place("HR", ONE_LINE);
        assertThat(confirmed.confirm()).isTrue();
        assertThat(confirmed.fail("late failure")).isFalse();
        assertThat(confirmed.getStatus()).isEqualTo(OrderStatus.CONFIRMED);

        Order failed = Order.place("HR", ONE_LINE);
        assertThat(failed.fail("No carrier")).isTrue();
        assertThat(failed.confirm()).isFalse();
        assertThat(failed.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(failed.getFailureReason()).isEqualTo("No carrier");
    }

    @Test
    void failureReasonIsTruncatedToFitTheColumn() {
        Order order = Order.place("HR", ONE_LINE);
        order.fail("x".repeat(600));
        assertThat(order.getFailureReason()).hasSize(500);
    }
}
