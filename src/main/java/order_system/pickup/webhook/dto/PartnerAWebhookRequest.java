package order_system.pickup.webhook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PartnerAWebhookRequest(
        @NotNull Long orderId,
        @NotBlank String status
) {}
