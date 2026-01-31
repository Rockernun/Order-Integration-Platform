package order_system.pickup.outbox;

import static org.flywaydb.core.internal.util.JsonUtils.toJson;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import order_system.pickup.outbox.constant.AggregateType;
import order_system.pickup.outbox.constant.OutboxEventType;
import order_system.pickup.outbox.dto.OrderCreatedEventPayload;
import order_system.pickup.outbox.dto.OutboxEvent;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class OutboxRepository {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

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
        String sql = """
        INSERT INTO outbox_events(event_type, aggregate_type, aggregate_id, payload)
        VALUES (:eventType, :aggregateType, :aggregateId, CAST(:payload AS JSON))
        """;

        SqlParameterSource param = new MapSqlParameterSource()
                .addValue("eventType", OutboxEventType.ORDER_CREATED.name())
                .addValue("aggregateType", AggregateType.ORDER.name())
                .addValue("aggregateId", payload.orderId())
                .addValue("payload", toJson(payload));
        namedParameterJdbcTemplate.update(sql, param);
    }

    public List<OutboxEvent> findAndLockPending(int limit, String workerId) {
        String selectSql = """
        SELECT id, event_type, aggregate_type, aggregate_id, payload, status,
               retry_count, next_run_at, last_error, created_at
        FROM outbox_events
        WHERE status = 'PENDING'
          AND (next_run_at IS NULL OR next_run_at <= CURRENT_TIMESTAMP)
        ORDER BY id
        LIMIT :limit
        FOR UPDATE SKIP LOCKED
        """;

        List<OutboxEvent> events = namedParameterJdbcTemplate.query(
                selectSql,
                new MapSqlParameterSource("limit", limit),
                OUTBOX_ROW_MAPPER
        );

        if (events.isEmpty()) {
            return events;
        }

        String lockSql = """
        UPDATE outbox_events
        SET status = 'PROCESSING',
            locked_by = :workerId,
            locked_at = CURRENT_TIMESTAMP
        WHERE id = :id
        """;

        SqlParameterSource[] batch = events.stream()
                .map(e -> new MapSqlParameterSource()
                        .addValue("workerId", workerId)
                        .addValue("id", e.id()))
                .toArray(SqlParameterSource[]::new);

        namedParameterJdbcTemplate.batchUpdate(lockSql, batch);

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
        WHERE id = :id
          AND status = 'PROCESSING'
          AND locked_by = :workerId
        """;

        SqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("workerId", workerId);

        return namedParameterJdbcTemplate.update(sql, params);
    }

    public int markFailedOrRetry(Long id, int nextRetryCount, boolean toFailed) {
        String nextStatus = toFailed ? "FAILED" : "PENDING";

        String sql = """
        UPDATE outbox_events
        SET status = :status,
            retry_count = :retryCount,
            processed_at = CASE
                WHEN :status = 'FAILED' THEN CURRENT_TIMESTAMP
                ELSE processed_at
            END
        WHERE id = :id
        """;

        SqlParameterSource params = new MapSqlParameterSource()
                .addValue("status", nextStatus)
                .addValue("retryCount", nextRetryCount)
                .addValue("id", id);

        return namedParameterJdbcTemplate.update(sql, params);
    }

    public int markRetry(Long id, String workerId, int nextRetryCount, Instant nextRunAt, String lastError) {
        String sql = """
        UPDATE outbox_events
        SET status = 'PENDING',
            retry_count = :retryCount,
            next_run_at = :nextRunAt,
            last_error = :lastError,
            locked_by = NULL,
            locked_at = NULL
        WHERE id = :id
          AND status = 'PROCESSING'
          AND locked_by = :workerId
        """;

        SqlParameterSource params = new MapSqlParameterSource()
                .addValue("retryCount", nextRetryCount)
                .addValue("nextRunAt", Timestamp.from(nextRunAt))
                .addValue("lastError", truncate(lastError, 500))
                .addValue("id", id)
                .addValue("workerId", workerId);

        return namedParameterJdbcTemplate.update(sql, params);
    }

    public int markFailed(Long id, String workerId, String lastError) {
        String sql = """
        UPDATE outbox_events
        SET status = 'FAILED',
            processed_at = CURRENT_TIMESTAMP,
            last_error = :lastError,
            locked_by = NULL,
            locked_at = NULL
        WHERE id = :id
          AND status = 'PROCESSING'
          AND locked_by = :workerId
        """;

        SqlParameterSource params = new MapSqlParameterSource()
                .addValue("lastError", truncate(lastError, 500))
                .addValue("id", id)
                .addValue("workerId", workerId);

        return namedParameterJdbcTemplate.update(sql, params);
    }

    public List<OutboxEvent> findByStatus(String status, int limit) {
        String sql = """
        SELECT id, event_type, aggregate_type, aggregate_id, payload,
               status, retry_count, next_run_at, last_error, created_at
        FROM outbox_events
        WHERE status = :status
        ORDER BY id DESC
        LIMIT :limit
        """;

        SqlParameterSource params = new MapSqlParameterSource()
                .addValue("status", status)
                .addValue("limit", limit);

        return namedParameterJdbcTemplate.query(sql, params, OUTBOX_ROW_MAPPER);
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
        WHERE id = :id
        """;

        return namedParameterJdbcTemplate.update(sql, new MapSqlParameterSource("id", id));
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
          AND locked_at < (CURRENT_TIMESTAMP - INTERVAL :seconds SECOND)
        """;

        SqlParameterSource params = new MapSqlParameterSource()
                .addValue("seconds", timeout.getSeconds());

        return namedParameterJdbcTemplate.update(sql, params);
    }

    public Optional<OutboxEvent> findById(Long id) {
        String sql = """
        SELECT id, event_type, aggregate_type, aggregate_id, payload,
               status, retry_count, next_run_at, last_error, created_at
        FROM outbox_events
        WHERE id = :id
        """;

        List<OutboxEvent> events = namedParameterJdbcTemplate.query(
                sql,
                new MapSqlParameterSource("id", id),
                OUTBOX_ROW_MAPPER
        );

        return events.stream().findFirst();
    }

    private static String truncate(String s, int maxLength) {
        if (s == null) {
            return null;
        }

        return s.length() <= maxLength ? s : s.substring(0, maxLength);
    }
}
