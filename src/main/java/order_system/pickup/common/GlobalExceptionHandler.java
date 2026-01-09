package order_system.pickup.common;

import java.util.Map;
import order_system.pickup.order.IdempotencyKeyInconsistentStateException;
import order_system.pickup.order.IdempotencyKeyMissingException;
import order_system.pickup.order.OrderNotFoundException;
import order_system.pickup.store.StoreNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(StoreNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, Object> handleStoreNotFound(StoreNotFoundException e) {
        return Map.of("code", "STORE_NOT_FOUND", "message", e.getMessage());
    }

    @ExceptionHandler(OrderNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, Object> handleOrderNotFound(OrderNotFoundException e) {
        return Map.of("code", "ORDER_NOT_FOUND", "message", e.getMessage());
    }

    @ExceptionHandler(IdempotencyKeyMissingException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleIdempotencyKeyMissing(IdempotencyKeyMissingException e) {
        return Map.of("code", "IDEMPOTENCY_KEY_MISSING", "message", e.getMessage());
    }

    @ExceptionHandler(IdempotencyKeyInconsistentStateException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handleIdempotencyInconsistentState(IdempotencyKeyInconsistentStateException e) {
        return Map.of("code", "IDEMPOTENCY_KEY_INCONSISTENT_STATE", "message", e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handleUnexpected(Exception e) {
        return Map.of("code", "INTERNAL_SERVER_ERROR", "message", "Unexpected error");
    }
}
