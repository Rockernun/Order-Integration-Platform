package order_system.pickup.ops.outbox;

import java.util.List;
import java.util.Map;
import order_system.pickup.outbox.dto.OutboxEvent;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ops/outbox")
public class OutboxOpsController {

    private final OutboxOpsService outboxOpsService;

    public OutboxOpsController(OutboxOpsService outboxOpsService) {
        this.outboxOpsService = outboxOpsService;
    }

    @GetMapping
    public List<OutboxEvent> list(
            @RequestParam(defaultValue = "FAILED") String status,
            @RequestParam(defaultValue = "50") int limit
    ) {
        int safeLimit = Math.min(limit, 200);
        return outboxOpsService.getOutboxEvents(status, safeLimit);
    }

    @PostMapping("/{id}/retry")
    public Map<String, Object> retry(@PathVariable Long id) {
        int updated = outboxOpsService.retryOutbox(id);
        return Map.of("id", id, "updated", updated);
    }
}
