package order_system.pickup.domain.store.exception;

public class StoreNotFoundException extends RuntimeException {
    public StoreNotFoundException(Long id) {
        super("Store 아이디가 " + id + "인 Store는 찾을 수 없습니다.");
    }
}
