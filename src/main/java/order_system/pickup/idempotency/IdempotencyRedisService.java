package order_system.pickup.idempotency;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class IdempotencyRedisService {

    private static final String PREFIX = "idempotency:";
    private static final String IN_PROGRESS = "IN_PROGRESS";
    private static final String DONE = "DONE";

    private final StringRedisTemplate redisTemplate;

    public IdempotencyRedisService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 멱등키를 선점
     * 해당 멱등키를 가지고 최초 요청한 경우 True 주문 생성
     * 중복된 요청이면 False 반환
     */
    public boolean tryAcquire(String idempotencyKey, Duration ttl) {
        String redisKey = toRedisKey(idempotencyKey);
        String value = IN_PROGRESS + ":" + UUID.randomUUID();

        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(redisKey, value, ttl);

        return Boolean.TRUE.equals(success);
    }

    /**
     * 정상적으로 처리된 주문이 있는지 확인
     * DONE:<OrderId> 형태면 orderId를 반환
     */
    public Optional<Long> findDoneOrderId(String idempotencyKey) {
        String redisKey = toRedisKey(idempotencyKey);
        String value = getValue(redisKey);

        if (value == null) {
            return Optional.empty();
        }

        if (value.startsWith(DONE + ":")) {
            try {
                return Optional.of(Long.parseLong(value.substring(DONE.length() + 1)));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /**
     * 주문을 정상적으로 생성하고 나서 결과를 저장
     */
    public void markDone(String idempotencyKey, Long orderId, Duration ttl) {
        String redisKey = toRedisKey(idempotencyKey);
        String value = DONE + ":" + orderId;

        redisTemplate.opsForValue().set(redisKey, value, ttl);
    }

    /**
     * 실패 시 락 해제
     * IN_PROGRESS 상태에서만 삭제 가능
     */
    public void releaseIfInProgress(String idempotencyKey) {
        String redisKey = toRedisKey(idempotencyKey);
        String value = getValue(redisKey);

        if (value != null && value.startsWith(IN_PROGRESS + ":")) {
            redisTemplate.delete(redisKey);
        }
    }

    private String getValue(String redisKey) {
        return redisTemplate.opsForValue().get(redisKey);
    }

    private String toRedisKey(String idempotencyKey) {
        return PREFIX + idempotencyKey;
    }
}
