package order_system.pickup.store;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Optional;
import order_system.pickup.store.dto.StoreCreateRequest;
import order_system.pickup.store.dto.StoreResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class StoreRepository {
    private final JdbcTemplate jdbcTemplate;

    public StoreRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Long save(StoreCreateRequest req) {
        String sql = "INSERT INTO stores(name, partner, partner_store_id, status) VALUES (?, ?, ?, 'ACTIVE')";

        // 데이터를 INSERT 한 직후 AUTO_INCREMENT로 생성되는 PK를 받아오기 위한 객체
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, req.name());
            ps.setString(2, req.partner());
            ps.setString(3, req.partnerStoreId());
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("DB ERROR: 생성된 Store 아이디를 받아오지 못했습니다.");
        }

        return key.longValue();
    }

    public Optional<StoreResponse> findById(Long id) {
        String sql = "SELECT * FROM stores WHERE id = ?";

        return jdbcTemplate.query(sql, rs -> {
            if (!rs.next()) {
                return Optional.empty();
            }
            return Optional.of(new StoreResponse(
                    rs.getLong("id"),
                    rs.getString("name"),
                    rs.getString("partner"),
                    rs.getString("partner_store_id"),
                    rs.getString("status")
            ));
        }, id);
    }
}
