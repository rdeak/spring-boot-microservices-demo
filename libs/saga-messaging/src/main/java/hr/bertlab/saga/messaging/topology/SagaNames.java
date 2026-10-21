package hr.bertlab.saga.messaging.topology;

import hr.bertlab.saga.messaging.SagaService;
import java.util.List;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;

public record SagaNames(@Nullable String prefix) {

    public SagaNames {
        prefix = prefix == null ? "" : prefix;
    }

    public String eventsExchange() {
        return prefix + "saga.events";
    }

    public String inExchange(SagaService service) {
        return prefix + service.key() + ".in";
    }

    public String partitionQueue(SagaService service, int partition) {
        return prefix + service.key() + ".q." + partition;
    }

    public List<String> partitionQueues(SagaService service, int partitions) {
        return IntStream.range(0, partitions)
                .mapToObj(i -> partitionQueue(service, i))
                .toList();
    }

    public String deadLetterExchange(SagaService service) {
        return prefix + service.key() + ".dlx";
    }

    public String deadLetterQueue(SagaService service) {
        return prefix + service.key() + ".dlq";
    }
}
