package order_system.pickup.order;

public class IdempotencyKeyMissingException extends RuntimeException {
    public IdempotencyKeyMissingException() {
        super("멱등키 헤더가 필요합니다.");
    }
}
