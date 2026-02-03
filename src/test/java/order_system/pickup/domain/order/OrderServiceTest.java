package order_system.pickup.domain.order;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import order_system.pickup.domain.order.dto.OrderCreateRequest;
import order_system.pickup.domain.order.dto.OrderResponse;
import order_system.pickup.domain.order.exception.IdempotencyKeyInconsistentStateException;
import order_system.pickup.domain.order.exception.IdempotencyKeyMissingException;
import order_system.pickup.domain.order.exception.OrderNotFoundException;
import order_system.pickup.domain.store.StoreRepository;
import order_system.pickup.domain.store.dto.StoreResponse;
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
import org.springframework.dao.DuplicateKeyException;

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

    @Test
    @DisplayName("createOrder(정상 흐름): 주문이 저장되면 outbox 저장 후 주문 조회 결과를 반환한다.")
    void createOrder_success() {
        String idempotencyKey = "idem-1";
        OrderCreateRequest request = new OrderCreateRequest(1L, 10000);

        StoreResponse store = new StoreResponse(1L, "A 가게", "국민카드", "gm-001", "ACTIVE");

        Long orderId = 1L;
        OrderResponse savedOrder = new OrderResponse(orderId, store.id(), "CREATED", request.totalPrice(), Instant.now());

        when(orderRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(storeRepository.findById(request.storeId())).thenReturn(Optional.of(store));
        when(orderRepository.save(request, idempotencyKey)).thenReturn(orderId);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(savedOrder));

        OrderResponse result = orderService.createOrder(request, idempotencyKey);

        Assertions.assertThat(result).isEqualTo(savedOrder);

        verify(orderRepository).findByIdempotencyKey(idempotencyKey);
        verify(storeRepository).findById(request.storeId());
        verify(orderRepository).save(request, idempotencyKey);
        verify(outboxRepository).saveOrderCreated(any(OrderCreatedEventPayload.class));
        verify(orderRepository).findById(orderId);
    }

    @Test
    @DisplayName("createOrder: 주문 저장 중 중복키 예외 발생 시 멱등키로 재조회해 기존 주문을 반환한다.")
    void createOrder_duplicateKey_thenReturnExistingOrder() {
        String idempotencyKey = "idem-1";
        OrderCreateRequest request = new OrderCreateRequest(1L, 10000);

        StoreResponse store = new StoreResponse(1L, "A 가게", "국민카드", "gm-001", "ACTIVE");
        OrderResponse existingOrder = new OrderResponse(2L, store.id(), "CREATED", request.totalPrice(), Instant.now());

        when(orderRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existingOrder));

        when(storeRepository.findById(request.storeId())).thenReturn(Optional.of(store));
        when(orderRepository.save(request, idempotencyKey)).thenThrow(new DuplicateKeyException("키가 중복됐습니다."));


        OrderResponse result = orderService.createOrder(request, idempotencyKey);

        Assertions.assertThat(result).isEqualTo(existingOrder);

        verify(orderRepository, times(2)).findByIdempotencyKey(idempotencyKey);
        verify(storeRepository).findById(request.storeId());
        verify(orderRepository).save(request, idempotencyKey);
        verify(outboxRepository, never()).saveOrderCreated(any(OrderCreatedEventPayload.class));
        verify(orderRepository, never()).findById(any());
    }

    @Test
    @DisplayName("createOrder: 중복키 예외 발생 및 재조회도 실패하면 예외를 발생시킨다.")
    void createOrder_duplicateKeyAndRetryFail_thenThrowException() {
        String idempotencyKey = "idem-1";
        OrderCreateRequest request = new OrderCreateRequest(1L, 10000);

        StoreResponse store = new StoreResponse(1L, "A 가게", "국민카드", "gm-001", "ACTIVE");

        when(orderRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(storeRepository.findById(request.storeId())).thenReturn(Optional.of(store));
        when(orderRepository.save(request, idempotencyKey)).thenThrow(new DuplicateKeyException("키가 중복됐습니다."));
        when(orderRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());

        Assertions.assertThatThrownBy(() -> orderService.createOrder(request, idempotencyKey))
                .isInstanceOf(IdempotencyKeyInconsistentStateException.class);

        verify(outboxRepository, never()).saveOrderCreated(any(OrderCreatedEventPayload.class));
        verify(orderRepository, never()).findById(any());
    }

    @Test
    @DisplayName("createOrder: 주문이 저장은 됐지만 잘못된 주문 아이디로 조회하는 경우 예외를 발생시킨다.")
    void createOrder_savedButUsingWrongId_thenThrowException() {
        String idempotencyKey = "idem-1";
        OrderCreateRequest request = new OrderCreateRequest(1L, 10000);

        StoreResponse store = new StoreResponse(1L, "A 가게", "국민카드", "gm-001", "ACTIVE");

        when(orderRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(storeRepository.findById(request.storeId())).thenReturn(Optional.of(store));
        when(orderRepository.save(request, idempotencyKey)).thenReturn(Long.MAX_VALUE);
        when(orderRepository.findById(Long.MAX_VALUE)).thenReturn(Optional.empty());

        Assertions.assertThatThrownBy(() -> orderService.createOrder(request, idempotencyKey))
                .isInstanceOf(OrderNotFoundException.class);

        verify(outboxRepository).saveOrderCreated(any(OrderCreatedEventPayload.class));
        verify(orderRepository).findById(Long.MAX_VALUE);
    }

    @Test
    @DisplayName("getOrder: 주문 정보가 존재하면 결과를 반환한다.")
    void getOrder_success() {
        Long orderId = 1L;
        OrderResponse response = new OrderResponse(orderId, 1L, "CREATED", 10000, Instant.now());

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(response));

        OrderResponse result = orderService.getOrder(orderId);

        Assertions.assertThat(result).isEqualTo(response);
        verify(orderRepository).findById(orderId);
    }

    @Test
    @DisplayName("getOrder: 주문 정보가 존재하지 않으면 예외를 발생시킨다.")
    void getOrder_notFound() {
        Long orderId = Long.MAX_VALUE;
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        Assertions.assertThatThrownBy(() -> orderService.getOrder(orderId))
                .isInstanceOf(OrderNotFoundException.class);

        verify(orderRepository).findById(orderId);
    }
}
