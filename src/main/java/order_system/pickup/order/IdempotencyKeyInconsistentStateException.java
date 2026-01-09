package order_system.pickup.order;

import org.springframework.dao.DuplicateKeyException;

public class IdempotencyKeyInconsistentStateException extends RuntimeException {
    public IdempotencyKeyInconsistentStateException(String idempotencyKey, DuplicateKeyException e) {
        super("멱등키 중복이 감지됐지만 기존 주문 재조회에 실패했습니다. key=" + idempotencyKey + ", message=" + e);
    }
}
