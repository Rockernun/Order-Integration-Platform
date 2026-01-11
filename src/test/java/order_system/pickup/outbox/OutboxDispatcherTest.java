package order_system.pickup.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class OutboxDispatcherTest {

    @Autowired OutboxDispatcher dispatcher;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void unknown_eventType_should_retry_and_then_fail() {
        jdbcTemplate.update("""
            INSERT INTO outbox_events(event_type, aggregate_type, aggregate_id, payload, status, retry_count)
            VALUES ('UNKNOWN_EVENT', 'ORDER', 999, CAST('{}' AS JSON), 'PENDING', 0)
        """);

        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM outbox_events WHERE event_type='UNKNOWN_EVENT' ORDER BY id DESC LIMIT 1",
                Long.class
        );

        for (int i = 0; i < 5; i++) {
            dispatcher.dispatch();
        }

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status, retry_count FROM outbox_events WHERE id = ?",
                id
        );

        assertThat(row.get("status")).isEqualTo("FAILED");
        assertThat(((Number) row.get("retry_count")).intValue()).isGreaterThanOrEqualTo(5);
    }
}