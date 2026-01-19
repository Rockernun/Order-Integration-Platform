package order_system.pickup.ops.outbox.dto;

import java.time.Instant;

public record OutboxEventResponse(
        Long id,
        String eventType,
        String aggregateType,
        Long aggregateId,
        String status,
        int retryCount,
        Instant nextRunAt,
        String lastError,
        Instant createdAt
) {}
