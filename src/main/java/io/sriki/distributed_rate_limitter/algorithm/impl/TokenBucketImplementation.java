package io.sriki.distributed_rate_limitter.algorithm.impl;

import io.sriki.distributed_rate_limitter.algorithm.RateLimiterAlgorithm;
import io.sriki.distributed_rate_limitter.model.BucketState;
import io.sriki.distributed_rate_limitter.model.RateLimiterAlgorithmRequest;
import io.sriki.distributed_rate_limitter.model.RateLimiterAlgorithmResponse;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class TokenBucketImplementation implements RateLimiterAlgorithm {

    private final ConcurrentHashMap<String, BucketState> tokenBucket =
        new ConcurrentHashMap<>();
    private static long BUCKET_CAPACITY = 100;
    private static int REFILL_RATE = 10;

    @Override
    public RateLimiterAlgorithmResponse isAllowed(
        RateLimiterAlgorithmRequest request
    ) {
        // fetch bucket if it is already present else create a new bucket with the full capacity
        BucketState bucket = tokenBucket.computeIfAbsent(request.key(), key ->
            new BucketState(BUCKET_CAPACITY, System.nanoTime())
        );
        // updating bucket should be an atomic operation so wrapping it around synchronized block
        synchronized (bucket) {
            // time taken to fill the complete bucket in seconds
            double fillTime = BUCKET_CAPACITY / (double) REFILL_RATE;

            // current time in nanoseconds
            long now = System.nanoTime();

            // number of nanoseconds passed from last refill time.
            long timeDifferenceInNanos = Math.max(
                0,
                now - bucket.getLastRefillTimestampInNanos()
            );
            // number of tokens to be refilled from last timestamp.
            double refillAmount =
                ((double) timeDifferenceInNanos * REFILL_RATE) / 1_000_000_000;
            // calculating number of tokens in bucket that should be present at this moment, if it is more than capacity, overflow tokens are ignored
            double filledTokens = Math.min(
                BUCKET_CAPACITY,
                refillAmount + bucket.getRemainingToken()
            );
            // allowed is set if current tokens is greater than required tokens
            boolean allowed = (filledTokens >= request.tokensRequired());

            // updating the bucket with new values;
            bucket.setRemainingToken(filledTokens);
            bucket.setLastRefillTimestampInNanos(now);
            if (allowed) {
                // in case the request is allowed, those tokens are removed from bucket
                bucket.setRemainingToken(
                    filledTokens - request.tokensRequired()
                );
                return new RateLimiterAlgorithmResponse(
                    allowed,
                    bucket.getRemainingToken(),
                    0
                );
            } else {
                return new RateLimiterAlgorithmResponse(
                    allowed,
                    bucket.getRemainingToken(),
                    getRetryAfterTime(
                        bucket.getRemainingToken(),
                        request.tokensRequired(),
                        REFILL_RATE
                    )
                );
            }
        }
    }

    private long getRetryAfterTime(
        double currentTokens,
        double requiredTokens,
        long refill_rate
    ) {
        return (long) (((requiredTokens - currentTokens) * Math.pow(10, 9)) /
            refill_rate);
    }
}
