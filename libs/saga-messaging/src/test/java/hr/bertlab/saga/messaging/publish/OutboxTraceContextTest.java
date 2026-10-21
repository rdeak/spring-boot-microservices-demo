package hr.bertlab.saga.messaging.publish;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** A service with no tracing bridge on the classpath gets these no-ops and must still work. */
class OutboxTraceContextTest {

    private final OutboxTraceContext tracing = new OutboxTraceContext(Tracer.NOOP, Propagator.NOOP);

    @Test
    void captureWithoutATracerIsEmpty() {
        assertThat(tracing.capture()).isEmpty();
    }

    @Test
    void restoreWithoutATracerYieldsASpanThatCanBeScopedAndEnded() {
        Span span =
                tracing.restore(Map.of("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"), "x");
        try (Tracer.SpanInScope scope = tracing.inScope(span)) {
            assertThat(span).isNotNull();
        } finally {
            span.end();
        }
    }
}
