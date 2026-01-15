package order_system.pickup.webhook;

import jakarta.validation.Valid;
import order_system.pickup.webhook.dto.PartnerAWebhookRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhooks/partner-a")
public class PartnerAWebhookController {

    @PostMapping("/orders")
    public ResponseEntity<Void> receiveOrderWebhook(
            @RequestHeader("X-Event-Id") String eventId,
            @RequestBody @Valid PartnerAWebhookRequest req
    ) {
        return ResponseEntity.ok().build();
    }
}
