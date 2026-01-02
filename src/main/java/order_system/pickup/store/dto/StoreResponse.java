package order_system.pickup.store.dto;

public record StoreResponse(
        Long id,
        String name,
        String partner,
        String partnerStoreId,
        String status
) {}
