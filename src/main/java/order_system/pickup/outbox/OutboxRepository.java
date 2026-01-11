package order_system.pickup.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import order_system.pickup.outbox.constant.AggregateType;
import order_system.pickup.outbox.constant.OutboxEventType;
import order_system.pickup.outbox.dto.OrderCreatedEventPayload;
import order_system.pickup.outbox.dto.OutboxEvent;
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

    public List<OutboxEvent> findPending(int limit) {
        String sql = """
            SELECT id, event_type, aggregate_type, aggregate_id, payload, status, retry_count, created_at
            FROM outbox_events
            WHERE status = 'PENDING'
            ORDER BY id
            LIMIT ?
            """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new OutboxEvent(
                rs.getLong("id"),
                rs.getString("event_type"),
                rs.getString("aggregate_type"),
                rs.getLong("aggregate_id"),
                rs.getString("payload"),
                rs.getString("status"),
                rs.getInt("retry_count"),
                rs.getTimestamp("created_at").toInstant()
        ), limit);
    }

    public int markProcessed(Long id) {
        String sql = """
            UPDATE outbox_events
            SET status = 'PROCESSED', processed_at = CURRENT_TIMESTAMP
            WHERE id = ? AND status = 'PENDING'
            """;

        return jdbcTemplate.update(sql, id);
    }

    public int markFailedOrRetry(Long id, int nextRetryCount, boolean toFailed) {
        String sql = """
            UPDATE outbox_events
            SET status = ?, retry_count = ?, processed_at = CASE WHEN ? = 'FAILED' THEN CURRENT_TIMESTAMP ELSE processed_at END
            WHERE id = ?
            """;

        String status = toFailed ? "FAILED" : "PENDING";
        return jdbcTemplate.update(sql, status, nextRetryCount, status, id);
    }

    private String toJson(OrderCreatedEventPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Outbox payload 직렬화 실패", e);
        }
    }
}
