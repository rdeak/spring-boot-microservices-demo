package hr.bertlab.saga.messaging;

import java.util.Locale;

public enum SagaService {
    ORDER,
    INVENTORY,
    SHIPPING;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
