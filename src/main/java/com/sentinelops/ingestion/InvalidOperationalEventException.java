package com.sentinelops.ingestion;

/** A permanent producer/data-contract error; route the original record to the DLT. */
public class InvalidOperationalEventException extends IllegalArgumentException {
    public InvalidOperationalEventException(String message) {
        super(message);
    }

    public InvalidOperationalEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
