package order_system.pickup.ops.outbox;

import java.util.List;
import order_system.pickup.ops.outbox.dto.OutboxEventResponse;
import order_system.pickup.outbox.OutboxRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ops/outbox")
public class OutboxOpsController {

    private final OutboxRepository outboxRepository;

    public OutboxOpsController(OutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    @GetMapping
    public List<OutboxEventResponse> list(
            @RequestParam(defaultValue = "FAILED") String status,
            @RequestParam(defaultValue = "50") int limit
    ) {
        int safeLimit = Math.min(Math.max(limit , 1), 200);
        return outboxRepository.findByStatus(status.toUpperCase(), safeLimit);
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<Void> retry(@PathVariable Long id) {
        int updated = outboxRepository.forceRetry(id);
        if (updated == 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().build();
    }
}
