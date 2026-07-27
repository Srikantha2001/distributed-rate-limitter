package io.sriki.distributed_rate_limitter.storage.impl;

import io.sriki.distributed_rate_limitter.model.BucketCheckResult;
import io.sriki.distributed_rate_limitter.model.BucketState;
import io.sriki.distributed_rate_limitter.storage.BucketStateStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

@Service
@Profile("!redis")
@Slf4j
public class InMemoryBucketStateStore implements BucketStateStore {

    private final ConcurrentHashMap<String, BucketState> tokenBucket =
            new ConcurrentHashMap<>();

    @Override
    public BucketCheckResult tryConsume(String key, int tokens, long capacity, double refillRate) {
        BucketState bucket = tokenBucket.computeIfAbsent(key, key1 ->
                new BucketState(capacity, System.nanoTime())
        );
        synchronized (bucket) {
            long now = System.nanoTime();

            // number of nanoseconds passed from last refill time.
            long timeDifferenceInNanos = Math.max(
                    0,
                    now - bucket.getLastRefillTimestampInNanos()
            );
            // number of tokens to be refilled from last timestamp.
            double refillAmount =
                    ((double) timeDifferenceInNanos * refillRate) / 1_000_000_000;
            // calculating number of tokens in bucket that should be present at this moment, if it is more than capacity, overflow tokens are ignored
            double filledTokens = Math.min(
                    capacity,
                    refillAmount + bucket.getRemainingToken()
            );
            bucket.setLastRefillTimestampInNanos(now);
            if (filledTokens >= tokens) {
                bucket.setRemainingToken(filledTokens - tokens);
                return new BucketCheckResult(true, bucket.getRemainingToken());
            } else {
                bucket.setRemainingToken(filledTokens);
                return new BucketCheckResult(false, bucket.getRemainingToken());
            }
        }
    }
}
