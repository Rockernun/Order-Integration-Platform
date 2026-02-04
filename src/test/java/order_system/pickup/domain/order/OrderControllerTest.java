package order_system.pickup.domain.order;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import order_system.pickup.common.GlobalExceptionHandler;
import order_system.pickup.domain.order.dto.OrderCreateRequest;
import order_system.pickup.domain.order.dto.OrderResponse;
import order_system.pickup.domain.order.exception.IdempotencyKeyMissingException;
import order_system.pickup.domain.order.exception.OrderNotFoundException;
import order_system.pickup.domain.store.exception.StoreNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OrderController.class)
@Import(GlobalExceptionHandler.class)
class OrderControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    OrderService orderService;

    @Test
    @DisplayName("POST /api/orders - 성공(201)")
    void createOrder_success_201() throws Exception {
        // given
        String key = "idem-001";
        String body = """
                {"storeId": 1, "totalPrice": 10000}
                """;

        Instant createdAt = Instant.now();
        OrderResponse response = new OrderResponse(
                1L, 1L, "CREATED", 10000, createdAt
        );

        when(orderService.createOrder(any(OrderCreateRequest.class), eq(key)))
                .thenReturn(response);

        // when & then
        mockMvc.perform(
                        post("/api/orders")
                                .contentType(APPLICATION_JSON)
                                .header("Idempotency-Key", key)
                                .content(body)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.storeId").value(1))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.totalPrice").value(10000))
                .andExpect(jsonPath("$.createdAt").value(createdAt.toString()));

        verify(orderService).createOrder(any(OrderCreateRequest.class), eq(key));
    }

    @Test
    @DisplayName("POST /api/orders - 멱등키 누락(400)")
    void createOrder_missingIdempotencyKey_400() throws Exception {
        // given
        String body = """
                {"storeId": 1, "totalPrice": 10000}
                """;

        when(orderService.createOrder(any(OrderCreateRequest.class), eq((String) null)))
                .thenThrow(new IdempotencyKeyMissingException());

        mockMvc.perform(
                        post("/api/orders")
                                .contentType(APPLICATION_JSON)
                                .content(body)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_MISSING"))
                .andExpect(jsonPath("$.message").exists());

        verify(orderService).createOrder(any(OrderCreateRequest.class), eq((String) null));
    }

    @Test
    @DisplayName("POST /api/orders - 요청 바디 검증 실패(400)")
    void createOrder_validationFail_400() throws Exception {
        String invalidBody = """
                {"totalPrice": 10000}
                """;

        mockMvc.perform(
                        post("/api/orders")
                                .contentType(APPLICATION_JSON)
                                .header("Idempotency-Key", "idem-001")
                                .content(invalidBody)
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(orderService);
    }

    @Test
    @DisplayName("POST /api/orders - StoreNotFound(404)")
    void createOrder_storeNotFound_404() throws Exception {
        // given
        String key = "idem-001";
        String body = """
                {"storeId": 999, "totalPrice": 10000}
                """;

        when(orderService.createOrder(any(OrderCreateRequest.class), eq(key)))
                .thenThrow(new StoreNotFoundException(999L));

        // when & then
        mockMvc.perform(
                        post("/api/orders")
                                .contentType(APPLICATION_JSON)
                                .header("Idempotency-Key", key)
                                .content(body)
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STORE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("GET /api/orders/{id} - 주문 조회 성공(200)")
    void getOrder_success_200() throws Exception {
        Long orderId = 1L;
        Instant createdAt = Instant.now();
        OrderResponse response = new OrderResponse(orderId, 1L, "CREATED", 10000, createdAt);

        when(orderService.getOrder(orderId)).thenReturn(response);

        mockMvc.perform(get("/api/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.storeId").value(1))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.totalPrice").value(10000))
                .andExpect(jsonPath("$.createdAt").value(createdAt.toString()));
    }

    @Test
    @DisplayName("GET /api/orders/{id} - OrderNotFound: 404 + code/message")
    void getOrder_notFound_404() throws Exception {
        // given
        long orderId = 999L;
        when(orderService.getOrder(orderId)).thenThrow(new OrderNotFoundException(orderId));

        // when & then
        mockMvc.perform(get("/api/orders/{id}", orderId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("예상치 못한 예외: 500 + INTERNAL_SERVER_ERROR")
    void unexpected_500() throws Exception {
        long orderId = 1L;
        when(orderService.getOrder(orderId)).thenThrow(new RuntimeException("Unexpected Error..."));

        mockMvc.perform(get("/api/orders/{id}", orderId))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("Unexpected error"));
    }
}