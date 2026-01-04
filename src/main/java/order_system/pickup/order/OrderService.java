package order_system.pickup.order;

import order_system.pickup.order.dto.OrderCreateRequest;
import order_system.pickup.order.dto.OrderResponse;
import order_system.pickup.store.StoreNotFoundException;
import order_system.pickup.store.StoreRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final StoreRepository storeRepository;
    private final OrderRepository orderRepository;

    public OrderService(StoreRepository storeRepository, OrderRepository orderRepository) {
        this.storeRepository = storeRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public OrderResponse createOrder(OrderCreateRequest req, String idempotencyKey) {
        validateIdempotencyKey(idempotencyKey);
        var existOrder = orderRepository.findByIdempotencyKey(idempotencyKey);

        if (existOrder.isPresent()) {
            return existOrder.get();
        }

        boolean existStore = storeRepository.findById(req.storeId()).isPresent();

        if (!existStore) {
            throw new StoreNotFoundException(req.storeId());
        }

        Long orderId;
        try {
            orderId = orderRepository.save(req, idempotencyKey);
        } catch (DuplicateKeyException e) {
            return orderRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException("Idempotency-Key 중복이 감지됐지만 기존 주문 조회에 실패했습니다: " + idempotencyKey, e));
        }

        return orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalStateException("주문이 생성되었으나 조회되지 않습니다. orderId=" + orderId));
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("요청 헤더 Idempotency-Key가 누락되었습니다.");
        }
    }
}
