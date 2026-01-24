package order_system.pickup.ops.outbox;

import java.util.List;
import order_system.pickup.outbox.OutboxRepository;
import order_system.pickup.outbox.dto.OutboxEvent;
import org.springframework.stereotype.Service;

@Service
public class OutboxOpsService {

    private final OutboxRepository outboxRepository;

    public OutboxOpsService(OutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    public List<OutboxEvent> getOutboxEvents(String status, int limit) {
        return outboxRepository.findByStatus(status, limit);
    }

    public int retryOutbox(Long id) {
        return outboxRepository.forceRetry(id);
    }
}
