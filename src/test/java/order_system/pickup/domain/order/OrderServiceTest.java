package order_system.pickup.domain.order;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import order_system.pickup.domain.order.dto.OrderCreateRequest;
import order_system.pickup.domain.order.dto.OrderResponse;
import order_system.pickup.domain.order.exception.IdempotencyKeyMissingException;
import order_system.pickup.domain.store.StoreRepository;
import order_system.pickup.domain.store.exception.StoreNotFoundException;
import order_system.pickup.outbox.OutboxRepository;
import order_system.pickup.outbox.dto.OrderCreatedEventPayload;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private StoreRepository storeRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OutboxRepository outboxRepository;

    @InjectMocks
    private OrderService orderService;

    @Test
    @DisplayName("createOrder: 멱등키가 NULL이면 예외를 던진다.")
    void createOrder_idempotencyKey_isNull() {
        OrderCreateRequest request = new OrderCreateRequest(1L, 10000);

        Assertions.assertThatThrownBy(() -> orderService.createOrder(request, null))
                .isInstanceOf(IdempotencyKeyMissingException.class);

        verifyNoInteractions(orderRepository, storeRepository, outboxRepository);
    }

    @Test
    @DisplayName("createOrder: 멱등키가 빈 값이면 예외를 던진다.")
    void createOrder_idempotencyKey_isBlank() {
        OrderCreateRequest request = new OrderCreateRequest(1L, 10000);

        Assertions.assertThatThrownBy(() -> orderService.createOrder(request, ""))
                .isInstanceOf(IdempotencyKeyMissingException.class);

        verifyNoInteractions(orderRepository, storeRepository, outboxRepository);
    }

    @Test
    @DisplayName("createOrder: 동일한 멱등키 주문이 이미 존재하면 기존 주문을 반환한다.")
    void createOrder_returnExistingOrder_whenIdempotencyKeyIsExists() {
        String idempotencyKey = "idem-1";
        OrderCreateRequest request = new OrderCreateRequest(1L, 10000);

        OrderResponse existingOrder = new OrderResponse(
                1L, 1L, "CREATED", 50000, Instant.now()
        );

        when(orderRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existingOrder));

        OrderResponse result = orderService.createOrder(request, idempotencyKey);

        Assertions.assertThat(result).isEqualTo(existingOrder);

        verify(orderRepository).findByIdempotencyKey(idempotencyKey);
        verify(storeRepository, never()).findById(any());
        verify(orderRepository, never()).save(any(), anyString());
        verify(outboxRepository, never()).saveOrderCreated(any(OrderCreatedEventPayload.class));
        verify(orderRepository, never()).findById(any());
    }

    @Test
    @DisplayName("createOrder: Store가 존재하지 않으면 예외를 발생시킨다.")
    void createOrder_storeNotFound() {
        String idempotencyKey = "idem-1";
        OrderCreateRequest request = new OrderCreateRequest(Long.MAX_VALUE, 10000);

        when(orderRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(storeRepository.findById(request.storeId())).thenReturn(Optional.empty());

        Assertions.assertThatThrownBy(() -> orderService.createOrder(request, idempotencyKey))
                .isInstanceOf(StoreNotFoundException.class);

        verify(orderRepository).findByIdempotencyKey(idempotencyKey);
        verify(storeRepository).findById(request.storeId());
        verify(orderRepository, never()).save(any(), anyString());
        verify(outboxRepository, never()).saveOrderCreated(any(OrderCreatedEventPayload.class));
        verify(orderRepository, never()).findById(any());
    }
}