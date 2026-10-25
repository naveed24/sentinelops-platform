package com.sentinelops.alert;

/** Durable delivery lifecycle; dispatch is implemented in a later slice. */
public enum AlertNotificationStatus {
    PENDING,
    SENDING,
    SENT,
    FAILED
}
