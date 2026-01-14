package order_system.pickup.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import order_system.pickup.domain.order.OrderRepository;
import order_system.pickup.domain.store.StoreRepository;
import order_system.pickup.outbox.constant.OutboxEventType;
import order_system.pickup.outbox.dto.OutboxEvent;
import order_system.pickup.partner.adapter.PartnerAAdapter;
import order_system.pickup.partner.client.PartnerAClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);

    private static final int BATCH_SIZE = 10;
    private static final int MAX_RETRY_COUNT = 5;

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final OrderRepository orderRepository;
    private final StoreRepository storeRepository;
    private final PartnerAClient partnerAClient;
    private final PartnerAAdapter partnerAAdapter;

    public OutboxDispatcher(OutboxRepository outboxRepository, ObjectMapper objectMapper,
                            OrderRepository orderRepository, StoreRepository storeRepository,
                            PartnerAClient partnerAClient, PartnerAAdapter partnerAAdapter) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.orderRepository = orderRepository;
        this.storeRepository = storeRepository;
        this.partnerAClient = partnerAClient;
        this.partnerAAdapter = partnerAAdapter;
    }

    @Scheduled(fixedDelay = 5000)
    public void dispatch() {
        var events = outboxRepository.findPending(BATCH_SIZE);
        if (events.isEmpty()) {
            return;
        }

        log.info("Outbox 디스패처 실행: size={}", events.size());

        for (OutboxEvent event : events) {
            try {
                handle(event);
                int updated = outboxRepository.markProcessed(event.id());

                if (updated == 1) {
                    log.info("outbox가 처리되었습니다: id={}, type={}, aggId={}",
                            event.id(), event.eventType(), event.aggregateId());
                } else {
                    log.info("outbox가 이미 처리된 상태거나 PENDING이 아닙니다: id={}", event.id());
                }
            } catch (Exception e) {
                int nextRetry = event.retryCount() + 1;
                boolean toFailed = nextRetry >= MAX_RETRY_COUNT;

                outboxRepository.markFailedOrRetry(event.id(), nextRetry, toFailed);

                log.warn("outbox 실패: id={}, retry={}, toFailed={}, reason={}",
                        event.id(), nextRetry, toFailed, e.toString(), e);
            }
        }
    }

    private void handle(OutboxEvent event) throws Exception {
        if ("ORDER_CREATED".equals(event.eventType())) {
            Long orderId = event.aggregateId();

            var order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new IllegalStateException("ORDER_CREATED인데 주문이 없습니다. orderId=" + orderId));
            var store = storeRepository.findById(order.storeId())
                    .orElseThrow(() -> new IllegalStateException("주문은 있는데 매장이 없습니다. storeId=" + order.storeId()));

            String partner = store.partner();
            if (partner == null || partner.isBlank()) {
                return;
            }

            if ("PartnerA".equalsIgnoreCase(partner)) {
                var req = partnerAAdapter.toPartnerA(order, store);

                boolean forceFail = false;
                partnerAClient.sendOrder(req, forceFail);
                return;
            }

            throw new IllegalArgumentException("지원하지 않는 partner=" + partner + " (storeId=" + store.id() + ")");
        }

        if (OutboxEventType.ORDER_CREATED.equals(event.eventType())) {
            var json = objectMapper.readTree(event.payload());
            log.info("mock handle ORDER_CREATED: payload={}", json.toString());
            return;
        }

        throw new IllegalArgumentException("알 수 없는 이벤트 타입입니다. " + event.eventType());
    }
}
