package order_system.pickup.domain.order;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper;

    public OrderService(StoreRepository storeRepository, OrderRepository orderRepository,
                        IdempotencyRedisService idempotencyRedisService,
                        ObjectMapper objectMapper) {
        this.storeRepository = storeRepository;
        this.orderRepository = orderRepository;
        this.idempotencyRedisService = idempotencyRedisService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public OrderResponse createOrder(OrderCreateRequest req, String idempotencyKey) {
        validateIdempotencyKey(idempotencyKey);

        Optional<OrderResponse> cached = findCachedDoneResponse(idempotencyKey);
        if (cached.isPresent()) {
            return cached.get();
        }

        // Redis 락 선점
        boolean acquired = idempotencyRedisService.tryAcquire(idempotencyKey, IN_PROGRESS_TTL);

        if (!acquired) {  // 락 선점 실패 시
            Optional<OrderResponse> cachedAgain = findCachedDoneResponse(idempotencyKey);
            if (cachedAgain.isPresent()) {
                return cachedAgain.get();
            }

            // 바로 409 에러를 반환
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
                OrderResponse existing = orderRepository.findByIdempotencyKey(idempotencyKey)
                        .orElseThrow(() -> new IllegalStateException(
                                "멱등키 중복이 감지되었지만 기존 주문 조회에 실패했습니다: " + idempotencyKey, e
                        ));

                cacheDoneResponse(idempotencyKey, existing);
                return existing;
            }

            OrderResponse created = orderRepository.findById(orderId)
                    .orElseThrow(() -> new OrderNotFoundException(orderId));
            cacheDoneResponse(idempotencyKey, created);

            return created;
        } catch (RuntimeException e) {
            idempotencyRedisService.releaseIfInProgress(idempotencyKey);
            throw e;
        }
    }

    public OrderResponse getOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    private Optional<OrderResponse> findCachedDoneResponse(String idempotencyKey) {
        return idempotencyRedisService.findDoneResponseJson(idempotencyKey)
                .flatMap(json -> {
                    try {
                        return Optional.of(objectMapper.readValue(json, OrderResponse.class));
                    } catch (Exception e) {
                        return Optional.empty();
                    }
                });
    }

    private void cacheDoneResponse(String idempotencyKey, OrderResponse response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            idempotencyRedisService.markDoneResponse(idempotencyKey, json, DONE_TTL);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("DONE 응답 캐싱에 실패했습니다.", e);
        }
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IdempotencyKeyMissingException();
        }
    }
}
