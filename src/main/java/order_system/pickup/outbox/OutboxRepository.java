package order_system.pickup.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import order_system.pickup.outbox.constant.AggregateType;
import order_system.pickup.outbox.constant.OutboxEventType;
import order_system.pickup.outbox.dto.OrderCreatedEventPayload;
import order_system.pickup.outbox.dto.OutboxEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class OutboxRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public OutboxRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    private static final RowMapper<OutboxEvent> OUTBOX_ROW_MAPPER = (rs, rowNum) -> new OutboxEvent(
            rs.getLong("id"),
            rs.getString("event_type"),
            rs.getString("aggregate_type"),
            rs.getLong("aggregate_id"),
            rs.getString("payload"),
            rs.getString("status"),
            rs.getInt("retry_count"),
            rs.getTimestamp("next_run_at") != null ? rs.getTimestamp("next_run_at").toInstant() : null,
            rs.getString("last_error"),
            rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : null
    );

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

    public List<OutboxEvent> findAndLockPending(int limit, String workerId) {
        String sql = """
        SELECT id, event_type, aggregate_type, aggregate_id, payload, status,
               retry_count, next_run_at, last_error, created_at
        FROM outbox_events
        WHERE status = 'PENDING'
          AND (next_run_at IS NULL OR next_run_at <= CURRENT_TIMESTAMP)
        ORDER BY id
        LIMIT ?
        FOR UPDATE SKIP LOCKED
        """;

        List<OutboxEvent> events = jdbcTemplate.query(sql, OUTBOX_ROW_MAPPER, limit);

        if (events.isEmpty()) {
            return events;
        }

        String lockSql = """
            UPDATE outbox_events 
            SET status = 'PROCESSING', 
                locked_by = ?, 
                locked_at = CURRENT_TIMESTAMP 
            WHERE id = ?
            """;

        jdbcTemplate.batchUpdate(
                lockSql,
                events,
                events.size(),
                (ps, event) -> {
                    ps.setString(1, workerId);
                    ps.setLong(2, event.id());
                }
        );

        return events;
    }

    public int markProcessed(Long id, String workerId) {
        String sql = """
            UPDATE outbox_events
            SET status = 'PROCESSED', 
                processed_at = CURRENT_TIMESTAMP,
                next_run_at = NULL,
                last_error = NULL,
                locked_by = NULL,
                locked_at = NULL
            WHERE id = ? 
                AND status = 'PROCESSING'
                AND locked_by = ?
            """;

        return jdbcTemplate.update(sql, id, workerId);
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

    public int markRetry(Long id, String workerId, int nextRetryCount, Instant nextRunAt, String lastError) {
        String sql = """
        UPDATE outbox_events
        SET status = 'PENDING',
            retry_count = ?,
            next_run_at = ?,
            last_error = ?,
            locked_by = NULL,
            locked_at = NULL
        WHERE id = ?
            AND status = 'PROCESSING'
            AND locked_by = ?
        """;

        return jdbcTemplate.update(
                sql,
                nextRetryCount,
                Timestamp.from(nextRunAt),
                truncate(lastError, 500),
                id,
                workerId
        );
    }

    public int markFailed(Long id, String workerId, int nextRetryCount, String lastError) {
        String sql = """
        UPDATE outbox_events
        SET status = 'FAILED',
            retry_count = ?,
            processed_at = CURRENT_TIMESTAMP,
            last_error = ?,
            locked_by = NULL,
            locked_at = NULL
        WHERE id = ?
            AND status = 'PROCESSING'
            AND locked_by = ?
        """;

        return jdbcTemplate.update(sql,
                nextRetryCount,
                truncate(lastError, 500),
                id,
                workerId);
    }

    public List<OutboxEvent> findByStatus(String status, int limit) {
        String sql = """
        SELECT id, event_type, aggregate_type, aggregate_id, payload,
                status, retry_count, next_run_at, last_error, created_at
        FROM outbox_events
        WHERE status = ?
        ORDER BY id DESC
        LIMIT ?
        """;

        return jdbcTemplate.query(sql, OUTBOX_ROW_MAPPER, status, limit);
    }

    public int forceRetry(Long id) {
        String sql = """
        UPDATE outbox_events
        SET status = 'PENDING',
            next_run_at = CURRENT_TIMESTAMP,
            processed_at = NULL,
            last_error = NULL,
            locked_by = NULL,
            locked_at = NULL
        WHERE id = ?
        """;

        return jdbcTemplate.update(sql, id);
    }

    public int recoverStuckProcessing(Duration timeout) {
        String sql = """
            UPDATE outbox_events
            SET status = 'PENDING',
                locked_by = NULL,
                locked_at = NULL,
                next_run_at = CURRENT_TIMESTAMP
            WHERE status = 'PROCESSING'
              AND locked_at IS NOT NULL
              AND locked_at < (CURRENT_TIMESTAMP - INTERVAL ? SECOND)
            """;

        return jdbcTemplate.update(sql, timeout.getSeconds());
    }

    public Optional<OutboxEvent> findById(Long id) {
        String sql = """
            SELECT id, event_type, aggregate_type, aggregate_id, payload,
                   status, retry_count, next_run_at, last_error, created_at
            FROM outbox_events
            WHERE id = ?
            """;
        List<OutboxEvent> events = jdbcTemplate.query(sql, OUTBOX_ROW_MAPPER, id);
        return events.stream().findFirst();
    }

    private static String truncate(String s, int maxLength) {
        if (s == null) {
            return null;
        }

        return s.length() <= maxLength ? s : s.substring(0, maxLength);
    }

    private String toJson(OrderCreatedEventPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Outbox payload 직렬화 실패", e);
        }
    }
}
