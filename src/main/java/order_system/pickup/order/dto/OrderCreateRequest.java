package order_system.pickup.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record OrderCreateRequest(
        @NotNull Long storeId,
        @NotNull @PositiveOrZero Integer totalPrice
) {}
