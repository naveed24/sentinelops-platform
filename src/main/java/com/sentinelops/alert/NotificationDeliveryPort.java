package com.sentinelops.alert;

/**
 * Delivery adapters must honor deliveryId as an idempotency key.
 * External delivery is at-least-once because a process can crash after send
 * but before the database acknowledges success.
 */
@FunctionalInterface
public interface NotificationDeliveryPort {
    void deliver(NotificationDeliveryPayload payload) throws Exception;
}
