package order_system.pickup.domain.order;

import lombok.RequiredArgsConstructor;
import order_system.pickup.domain.order.dto.OrderCreateRequest;
import order_system.pickup.domain.order.dto.OrderResponse;
import order_system.pickup.domain.order.exception.IdempotencyKeyInconsistentStateException;
import order_system.pickup.domain.order.exception.IdempotencyKeyMissingException;
import order_system.pickup.domain.order.exception.OrderNotFoundException;
import order_system.pickup.domain.store.exception.StoreNotFoundException;
import order_system.pickup.domain.store.StoreRepository;
import order_system.pickup.outbox.OutboxRepository;
import order_system.pickup.outbox.dto.OrderCreatedEventPayload;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final StoreRepository storeRepository;
    private final OrderRepository orderRepository;
    private final OutboxRepository outboxRepository;

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
                    .orElseThrow(() -> new IdempotencyKeyInconsistentStateException(idempotencyKey, e));
        }

        var payload = OrderCreatedEventPayload.of(
                orderId,
                req.storeId(),
                req.totalPrice(),
                idempotencyKey,
                java.util.UUID.randomUUID().toString(),
                java.time.Instant.now()
        );
        outboxRepository.saveOrderCreated(payload);

        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    public OrderResponse getOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IdempotencyKeyMissingException();
        }
    }
}
