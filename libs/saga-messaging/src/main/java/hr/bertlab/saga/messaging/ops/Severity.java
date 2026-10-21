package hr.bertlab.saga.messaging.ops;

public enum Severity {
    WARN,
    ERROR,
    /** Data may be inconsistent until someone intervenes (for example a failed compensation). */
    CRITICAL
}
