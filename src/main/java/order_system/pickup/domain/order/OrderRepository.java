package order_system.pickup.domain.order;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import order_system.pickup.domain.order.dto.OrderCreateRequest;
import order_system.pickup.domain.order.dto.OrderResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class OrderRepository {

    private final JdbcTemplate jdbcTemplate;

    public Long save(OrderCreateRequest req, String idempotencyKey) {
        String sql = "INSERT INTO orders(store_id, status, total_price, idempotency_key) VALUES (?, 'CREATED', ?, ?)";

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, req.storeId());
            ps.setInt(2, req.totalPrice());
            ps.setString(3, idempotencyKey);
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("생성된 주문 정보의 id를 찾을 수 없습니다.");
        }

        return key.longValue();
    }

    public Optional<OrderResponse> findById(Long id) {
        String sql = "SELECT id, store_id, status, total_price, created_at FROM orders WHERE id = ?";

        return jdbcTemplate.query(sql, rs -> {
            if (!rs.next()) {
                return Optional.empty();
            }

            Timestamp ts = rs.getTimestamp("created_at");
            Instant createdAt = null;

            if (ts != null) {
                createdAt = ts.toInstant();
            }

            return Optional.of(new OrderResponse(
                    rs.getLong("id"),
                    rs.getLong("store_id"),
                    rs.getString("status"),
                    rs.getInt("total_price"),
                    createdAt
            ));
        }, id);
    }

    public Optional<OrderResponse> findByIdempotencyKey(String idempotencyKey) {
        String sql = "SELECT id, store_id, status, total_price, created_at FROM orders WHERE idempotency_key = ?";

        return jdbcTemplate.query(sql, rs -> {
            if (!rs.next()) {
                return Optional.empty();
            }

            Timestamp ts = rs.getTimestamp("created_at");
            Instant createdAt = null;

            if (ts != null) {
                createdAt = ts.toInstant();
            }

            return Optional.of(new OrderResponse(
                    rs.getLong("id"),
                    rs.getLong("store_id"),
                    rs.getString("status"),
                    rs.getInt("total_price"),
                    createdAt
            ));
        }, idempotencyKey);
    }

    public int updateStatus(Long orderId, String status) {
        String sql = "UPDATE orders SET status = ? WHERE id = ?";
        return jdbcTemplate.update(sql, status, orderId);
    }
}
