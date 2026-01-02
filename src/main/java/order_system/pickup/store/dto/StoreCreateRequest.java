package order_system.pickup.store.dto;

import jakarta.validation.constraints.NotBlank;

public record StoreCreateRequest(
        @NotBlank String name,
        String partner,
        String partnerStoreId
) {}
