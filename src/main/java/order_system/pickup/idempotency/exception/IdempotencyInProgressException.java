package order_system.pickup.idempotency.exception;

public class IdempotencyInProgressException extends RuntimeException {
    public IdempotencyInProgressException(String key) {
        super("해당 멱등키 요청은 현재 처리 중입니다. 잠시 후 재시도 해주세요. key=" + key);
    }
}
