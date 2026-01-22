package order_system.pickup.domain.order;

import java.time.Duration;
import java.util.Optional;
import order_system.pickup.domain.order.dto.OrderCreateRequest;
import order_system.pickup.domain.order.dto.OrderResponse;
import order_system.pickup.domain.order.exception.IdempotencyKeyMissingException;
import order_system.pickup.domain.order.exception.OrderNotFoundException;
import order_system.pickup.domain.store.exception.StoreNotFoundException;
import order_system.pickup.domain.store.StoreRepository;
import order_system.pickup.idempotency.IdempotencyRedisService;
import order_system.pickup.idempotency.exception.IdempotencyInProgressException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private static final Duration IN_PROGRESS_TTL = Duration.ofSeconds(60);
    private static final Duration DONE_TTL = Duration.ofHours(24);

    private final StoreRepository storeRepository;
    private final OrderRepository orderRepository;
    private final IdempotencyRedisService idempotencyRedisService;

    public OrderService(StoreRepository storeRepository, OrderRepository orderRepository,
                        IdempotencyRedisService idempotencyRedisService) {
        this.storeRepository = storeRepository;
        this.orderRepository = orderRepository;
        this.idempotencyRedisService = idempotencyRedisService;
    }

    @Transactional
    public OrderResponse createOrder(OrderCreateRequest req, String idempotencyKey) {
        validateIdempotencyKey(idempotencyKey);

        Optional<Long> doneOrderId = idempotencyRedisService.findDoneOrderId(idempotencyKey);
        if (doneOrderId.isPresent()) {
            return orderRepository.findById(doneOrderId.get())
                    .orElseThrow(() -> new OrderNotFoundException(doneOrderId.get()));
        }

        boolean acquiredRedisKey = idempotencyRedisService.tryAcquire(idempotencyKey, IN_PROGRESS_TTL);

        if (!acquiredRedisKey) {
            Optional<Long> maybeDone = waitUntilDone(idempotencyKey, 10, 20);
            if (maybeDone.isPresent()) {
                return orderRepository.findById(maybeDone.get())
                        .orElseThrow(() -> new OrderNotFoundException(maybeDone.get()));
            }

            var existing = orderRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                idempotencyRedisService.markDone(idempotencyKey, existing.get().id(), DONE_TTL);
                return existing.get();
            }

            throw new IdempotencyInProgressException(idempotencyKey);
        }

        try {
            boolean existStore = storeRepository.findById(req.storeId()).isPresent();
            if (!existStore) {
                throw new StoreNotFoundException(req.storeId());
            }

            Long orderId;
            try {
                orderId = orderRepository.save(req, idempotencyKey);
            } catch (DuplicateKeyException e) {
                return orderRepository.findByIdempotencyKey(idempotencyKey)
                        .map(order -> {
                            idempotencyRedisService.markDone(idempotencyKey, order.id(), DONE_TTL);
                            return order;
                        })
                        .orElseThrow(() -> new IllegalStateException(
                                "멱등키 중복이 감지되었지만 기존 주문 조회에 실패했습니다: " + idempotencyKey, e
                        ));
            }

            OrderResponse created = orderRepository.findById(orderId)
                    .orElseThrow(() -> new OrderNotFoundException(orderId));

            idempotencyRedisService.markDone(idempotencyKey, orderId, DONE_TTL);
            return created;
        } catch (RuntimeException e) {
            idempotencyRedisService.releaseIfInProgress(idempotencyKey);
            throw e;
        }
    }

    private Optional<Long> waitUntilDone(String key, int maxAttempts, long sleepMillis) {
        for (int i = 0; i < maxAttempts; i++) {
            Optional<Long> done = idempotencyRedisService.findDoneOrderId(key);
            if (done.isPresent()) {
                return done;
            }

            try {
                Thread.sleep(sleepMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IdempotencyKeyMissingException();
        }
    }
}
