package order_system.pickup.outbox;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import order_system.pickup.outbox.dto.OutboxEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);

    private static final int BATCH_SIZE = 50;
    private static final int MAX_RETRY_ATTEMPTS = 5;
    private static final Duration PROCESSING_TIMEOUT = Duration.ofMinutes(2);
    private final OutboxRepository outboxRepository;

    private final String workerId = "worker:" + UUID.randomUUID();

    public OutboxDispatcher(OutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    @Scheduled(fixedDelay = 1000)
    public void dispatch() {
        int claimed = outboxRepository.claimPending(BATCH_SIZE, workerId);
        if (claimed == 0) {
            return;
        }

        List<OutboxEvent> events = outboxRepository.findAndLockPending(BATCH_SIZE, workerId);
        log.info("Outbox 디스패처 실행: claimed={}, fetched={}, workerId={}", claimed, events.size(), workerId);

        for (OutboxEvent event : events) {
            try {
                handle(event);

                int updated = outboxRepository.markProcessed(event.id(), workerId);
                if (updated == 0) {
                    log.warn("markProcessed 실패: id={}, workerId={}", event.id(), workerId);
                }
            } catch (Exception e) {
                onFailure(event, e);
            }
        }
    }

    @Scheduled(fixedDelay = 30000)
    public void recoverStuck() {
        int recovered = outboxRepository.recoverStuckProcessing(PROCESSING_TIMEOUT);
        if (recovered > 0) {
            log.warn("stuck PROCESSING 복구: recovered={}", recovered);
        }
    }

    private void handle(OutboxEvent event) {
        String eventType = event.eventType();

        if ("ORDER_CREATED".equals(eventType)) {
            // TODO: 향후에 실제 파트너 어댑터 혹은 클라이언트 호출
            log.debug("Handle ORDER_CREATED: id={}, aggregateId={}", event.id(), event.aggregateId());
            return;
        }

        throw new IllegalArgumentException("알 수 없는 이벤트 타입입니다. " + eventType);
    }

    private void onFailure(OutboxEvent event, Exception e) {
        int nextRetry = event.retryCount() + 1;

        boolean toFailed = nextRetry > MAX_RETRY_ATTEMPTS;
        String reason = e.getClass().getSimpleName() + ": " + e.getMessage();

        if (toFailed) {
            int updated = outboxRepository.markFailed(event.id(), workerId, nextRetry, reason);
            log.warn("outbox 실패 → FAILED(DLQ): id={}, retry={}, updated={}, reason={}",
                    event.id(), nextRetry, updated, reason, e);
            return;
        }

        Instant nextRunAt = Instant.now().plusSeconds(backoffTime(nextRetry));
        int updated = outboxRepository.markRetry(event.id(), workerId, nextRetry, nextRunAt, reason);

        log.warn("outbox 실패 → 재시도 예약: id={}, retry={}, nextRunAt={}, updated={}, reason={}",
                event.id(), nextRetry, nextRunAt, updated, reason, e);
    }

    private long backoffTime(int retryCount) {
        long backoffSeconds = 120;

        if (retryCount == 1) {
            backoffSeconds = 5;
        }

        if (retryCount == 2) {
            backoffSeconds = 15;
        }

        if (retryCount == 3) {
            backoffSeconds = 30;
        }

        if (retryCount == 4) {
            backoffSeconds = 60;
        }

        return backoffSeconds;
    }
}
