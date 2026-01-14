package order_system.pickup.partner.adapter;

import order_system.pickup.domain.order.dto.OrderResponse;
import order_system.pickup.domain.store.dto.StoreResponse;
import order_system.pickup.partner.dto.PartnerAOrderRequest;
import org.springframework.stereotype.Component;

@Component
public class PartnerAAdapter implements PartnerOrderAdapter {

    @Override
    public PartnerAOrderRequest toPartnerA(OrderResponse order, StoreResponse store) {
        if (store.partnerStoreId() != null || store.partnerStoreId().isBlank()) {
            throw new IllegalStateException("partnerStoreId 정보가 없어 PartnerA로 주문을 전송할 수 없습니다. storeId=" + store.id());
        }

        return new PartnerAOrderRequest(
                order.id(),
                store.partnerStoreId(),
                order.totalPrice()
        );
    }
}
