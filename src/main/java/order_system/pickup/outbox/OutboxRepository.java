package order_system.pickup.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import order_system.pickup.outbox.constant.AggregateType;
import order_system.pickup.outbox.constant.OutboxEventType;
import order_system.pickup.outbox.dto.OrderCreatedEventPayload;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OutboxRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public OutboxRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void saveOrderCreated(OrderCreatedEventPayload payload) {
        String sql = "INSERT INTO outbox_events(event_type, aggregate_type, aggregate_id, payload) VALUES (?, ?, ?, CAST(? AS JSON))";

        jdbcTemplate.update(
                sql,
                OutboxEventType.ORDER_CREATED,
                AggregateType.ORDER,
                payload.orderId(),
                toJson(payload)
        );
    }

    private String toJson(OrderCreatedEventPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Outbox payload 직렬화 실패", e);
        }
    }
}
