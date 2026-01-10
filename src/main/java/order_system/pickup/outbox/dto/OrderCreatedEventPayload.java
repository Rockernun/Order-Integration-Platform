package order_system.pickup.outbox.dto;

import java.time.Instant;

public record OrderCreatedEventPayload(
        String eventId,
        int schemaVersion,
        Instant occurredAt,
        Long orderId,
        Long storeId,
        Integer totalPrice,
        String idempotencyKey
) {
    public static final int SCHEMA_VERSION = 1;

    public static OrderCreatedEventPayload of(Long orderId,
                                              Long storeId,
                                              Integer totalPrice,
                                              String idempotencyKey,
                                              String eventId,
                                              Instant occurredAt) {

        return new OrderCreatedEventPayload(
                eventId,
                SCHEMA_VERSION,
                occurredAt,
                orderId,
                storeId,
                totalPrice,
                idempotencyKey
        );
    }
}
