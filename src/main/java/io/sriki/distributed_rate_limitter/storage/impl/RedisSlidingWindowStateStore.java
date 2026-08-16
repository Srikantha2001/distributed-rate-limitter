package io.sriki.distributed_rate_limitter.storage.impl;

import io.sriki.distributed_rate_limitter.model.SlidingWindowCheckResult;
import io.sriki.distributed_rate_limitter.storage.SlidingWindowStateStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Profile("redis")
@Slf4j
public class RedisSlidingWindowStateStore implements SlidingWindowStateStore {

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<List> script;

    public RedisSlidingWindowStateStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        DefaultRedisScript<List> s = new DefaultRedisScript<>();
        s.setLocation(new ClassPathResource("lua/sliding_window.lua"));
        s.setResultType(List.class);
        this.script = s;
    }

    @Override
    public SlidingWindowCheckResult tryConsume(String key, int tokens, int windowSizeInSeconds, int maxRequests) {
        List<Object> result = redisTemplate.execute(
                script,
                List.of(key),
                String.valueOf(tokens),
                String.valueOf(windowSizeInSeconds),
                String.valueOf(maxRequests)
        );
        boolean allowed = ((Long) result.get(0)) == 1L;
        long currentCount = ((Number) result.get(1)).longValue();
        long oldestTimestampMs = ((Number) result.get(2)).longValue();
        return new SlidingWindowCheckResult(allowed, currentCount, oldestTimestampMs);
    }
}
