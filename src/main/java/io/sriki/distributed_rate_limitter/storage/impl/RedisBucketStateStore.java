package io.sriki.distributed_rate_limitter.storage.impl;

import io.sriki.distributed_rate_limitter.model.BucketCheckResult;
import io.sriki.distributed_rate_limitter.storage.BucketStateStore;
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
public class RedisBucketStateStore implements BucketStateStore {

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<List> script;

    public RedisBucketStateStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        DefaultRedisScript<List> s = new DefaultRedisScript<>();
        s.setLocation(new ClassPathResource("lua/token_bucket.lua"));
        s.setResultType(List.class);
        this.script = s;
    }

    @Override
    public BucketCheckResult tryConsume(String key, int tokens, long capacity, double refillRate) {
        List<Object> result = redisTemplate.execute(
                script,
                List.of(key),
                String.valueOf(tokens),
                String.valueOf(capacity),
                String.valueOf(refillRate)
        );
        boolean allowed = ((Long) result.get(0)) == 1L;
        double remaining = ((Number) result.get(1)).doubleValue();
        return new BucketCheckResult(allowed, remaining);
    }
}
