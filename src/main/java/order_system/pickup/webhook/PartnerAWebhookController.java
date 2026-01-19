package order_system.pickup.webhook;

import jakarta.validation.Valid;
import order_system.pickup.webhook.dto.PartnerAWebhookRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/webhooks/partner-a")
public class PartnerAWebhookController {

    @Value("${partner.webhook.partner-a.token}") private String partnerAToken;
    private final PartnerAWebhookService partnerAWebhookService;

    public PartnerAWebhookController(PartnerAWebhookService partnerAWebhookService) {
        this.partnerAWebhookService = partnerAWebhookService;
    }

    @PostMapping("/orders")
    public ResponseEntity<Void> receiveOrderWebhook(
            @RequestHeader("X-Event-Id") String eventId,
            @RequestHeader("X-Partner-Token") String token,
            @RequestBody @Valid PartnerAWebhookRequest req
    ) {
        if (!token.equals(partnerAToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다.");
        }

        partnerAWebhookService.handle(eventId, req);
        return ResponseEntity.ok().build();
    }
}
