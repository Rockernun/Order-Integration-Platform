package order_system.pickup.webhook;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WebhookDuplicateRepository {

    private final JdbcTemplate jdbcTemplate;

    public WebhookDuplicateRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insertEvent(String partner, String eventId, Long orderId, String status, String payloadJson) {
        String sql = """
                INSERT INTO webhook_events(partner, event_id, order_id, status, payload)
                VALUES (?, ?, ?, ?, CAST(? AS JSON))
                """;

        jdbcTemplate.update(sql, partner, eventId, orderId, status, payloadJson);
    }
}
