package order_system.pickup.webhook;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import order_system.pickup.domain.order.OrderRepository;
import order_system.pickup.domain.order.exception.OrderNotFoundException;
import order_system.pickup.webhook.dto.PartnerAWebhookRequest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PartnerAWebhookService {

    private final WebhookDuplicateRepository webhookDuplicateRepository;
    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;

    public PartnerAWebhookService(
            WebhookDuplicateRepository webhookDuplicateRepository,
            OrderRepository orderRepository,
            ObjectMapper objectMapper)
    {
        this.webhookDuplicateRepository = webhookDuplicateRepository;
        this.orderRepository = orderRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void handle(String eventId, PartnerAWebhookRequest req) {
        try {
            webhookDuplicateRepository.insertEvent(
                    "PartnerA",
                    eventId,
                    req.orderId(),
                    req.status(),
                    toJson(req)
            );
        } catch (DuplicateKeyException e) {
            return;
        }

        int updated = orderRepository.updateStatus(req.orderId(), req.status());
        if (updated == 0) {
            throw new OrderNotFoundException(req.orderId());
        }
    }

    private String toJson(PartnerAWebhookRequest req) {
        try {
            return objectMapper.writeValueAsString(req);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Webhook payload 직렬화 실패", e);
        }
    }
}
