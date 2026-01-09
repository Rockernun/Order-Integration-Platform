package order_system.pickup.order;

public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(Long orderId) {
        super("주문이 생성되었으나 조회되지 않습니다: " + orderId);
    }
}
