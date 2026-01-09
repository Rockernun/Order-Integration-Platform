package order_system.pickup.order;

public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(Long orderId) {
        super("orderId: " + orderId + "에 대한 주문 정보를 찾을 수 없습니다.");
    }
}
