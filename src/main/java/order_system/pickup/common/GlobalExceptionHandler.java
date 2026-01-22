package order_system.pickup.common;

import java.util.Map;
import order_system.pickup.domain.order.exception.IdempotencyKeyInconsistentStateException;
import order_system.pickup.domain.order.exception.IdempotencyKeyMissingException;
import order_system.pickup.domain.order.exception.OrderNotFoundException;
import order_system.pickup.domain.store.exception.StoreNotFoundException;
import order_system.pickup.idempotency.exception.IdempotencyInProgressException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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

    @ExceptionHandler(IdempotencyInProgressException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, Object> handleIdempotencyInProgress(IdempotencyInProgressException e) {
        return Map.of("code", "IDEMPOTENCY_IN_PROGRESS", "message", e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handleUnexpected(Exception e) {
        log.error("Unexpected error", e);
        return Map.of("code", "INTERNAL_SERVER_ERROR", "message", "Unexpected error");
    }
}
