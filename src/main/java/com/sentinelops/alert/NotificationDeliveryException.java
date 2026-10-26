package com.sentinelops.alert;

/** Safe provider error: do not include response bodies or secrets in its message. */
public class NotificationDeliveryException extends RuntimeException {
    public NotificationDeliveryException(String message) {
        super(message);
    }
}
