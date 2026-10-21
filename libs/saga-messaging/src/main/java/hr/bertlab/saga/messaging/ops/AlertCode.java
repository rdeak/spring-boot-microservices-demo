package hr.bertlab.saga.messaging.ops;

public enum AlertCode {
    MESSAGE_DEAD_LETTERED(Severity.ERROR),
    COMPENSATION_FAILED(Severity.CRITICAL),
    POISON_MESSAGE(Severity.ERROR),
    DLQ_NOT_EMPTY(Severity.ERROR),
    OUTBOX_LAG(Severity.ERROR),
    SAGA_STUCK(Severity.WARN);

    private final Severity severity;

    AlertCode(Severity severity) {
        this.severity = severity;
    }

    public Severity severity() {
        return severity;
    }
}
