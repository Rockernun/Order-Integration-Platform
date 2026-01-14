package order_system.pickup.partner.adapter;

import order_system.pickup.domain.order.dto.OrderResponse;
import order_system.pickup.domain.store.dto.StoreResponse;
import order_system.pickup.partner.dto.PartnerAOrderRequest;

public interface PartnerOrderAdapter {
    PartnerAOrderRequest toPartnerA(OrderResponse order, StoreResponse store);
}
