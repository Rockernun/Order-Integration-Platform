package order_system.pickup.partner.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PartnerAOrderRequest(
        @NotNull long externalOrderId,
        @NotBlank String partnerStoreId,
        @NotNull Integer amount
) {}
