package order_system.pickup.order.dto;

import java.time.Instant;

public record OrderResponse(
        Long id,
        Long storeId,
        String status,
        Integer totalPrice,
        Instant createdAt
) {}
