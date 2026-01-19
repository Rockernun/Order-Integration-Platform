package order_system.pickup.outbox.dto;

import java.time.Instant;

public record OutboxEvent(
        Long id,
        String eventType,
        String aggregateType,
        Long aggregateId,
        String payload,
        String status,
        int retryCount,
        Instant nextRunAt,
        String lastError,
        Instant createdAt
) {}
