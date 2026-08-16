package io.sriki.distributed_rate_limitter.storage.impl;

import io.sriki.distributed_rate_limitter.model.SlidingWindowCheckResult;
import io.sriki.distributed_rate_limitter.storage.SlidingWindowStateStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Profile("!redis")
@Slf4j
public class InMemorySlidingWindowStateStore implements SlidingWindowStateStore {

    private final ConcurrentHashMap<String, TreeMap<Long, Long>> windowStore = new ConcurrentHashMap<>();

    @Override
    public SlidingWindowCheckResult tryConsume(String key, int tokens, int windowSizeInSeconds, int maxRequests) {
        TreeMap<Long, Long> timestamps = windowStore.computeIfAbsent(key, k -> new TreeMap<>());
        synchronized (timestamps) {
            long nowMs = Instant.now().toEpochMilli();
            long windowStart = nowMs - (windowSizeInSeconds * 1000L);

            timestamps.headMap(windowStart).clear();

            long currentCount = timestamps.values().stream().mapToLong(Long::longValue).sum();
            if (currentCount + tokens <= maxRequests) {
                timestamps.merge(nowMs, (long) tokens, Long::sum);
                return new SlidingWindowCheckResult(true, currentCount + tokens, 0);
            } else {
                long oldest = timestamps.isEmpty() ? nowMs : timestamps.firstKey();
                return new SlidingWindowCheckResult(false, currentCount, oldest);
            }
        }
    }
}
