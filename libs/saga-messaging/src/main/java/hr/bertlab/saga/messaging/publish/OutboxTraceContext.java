package hr.bertlab.saga.messaging.publish;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.util.LinkedHashMap;
import java.util.Map;

public class OutboxTraceContext {

    private final Tracer tracer;
    private final Propagator propagator;

    public OutboxTraceContext(Tracer tracer, Propagator propagator) {
        this.tracer = tracer;
        this.propagator = propagator;
    }

    public Map<String, String> capture() {
        Map<String, String> fields = new LinkedHashMap<>();
        TraceContext current = tracer.currentTraceContext().context();
        if (current != null) {
            propagator.inject(current, fields, (carrier, key, value) -> fields.put(key, value));
        }
        return fields;
    }

    public Span restore(Map<String, String> context, String spanName) {
        return propagator
                .extract(context, (carrier, key) -> carrier.get(key))
                .name(spanName)
                .start();
    }

    public Tracer.SpanInScope inScope(Span span) {
        return tracer.withSpan(span);
    }
}
