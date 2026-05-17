package io.sriki.distributed_rate_limitter.algorithm.impl;

import io.sriki.distributed_rate_limitter.algorithm.RateLimiterAlgorithm;
import io.sriki.distributed_rate_limitter.config.TokenBucketConfigurationProperties;
import io.sriki.distributed_rate_limitter.model.BucketState;
import io.sriki.distributed_rate_limitter.model.RateLimiterAlgorithmRequest;
import io.sriki.distributed_rate_limitter.model.RateLimiterAlgorithmResponse;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class TokenBucketImplementation implements RateLimiterAlgorithm {

    private final TokenBucketConfigurationProperties tokenBucketConfigurationProperties;
    private final ConcurrentHashMap<String, BucketState> tokenBucket =
        new ConcurrentHashMap<>();
    public TokenBucketImplementation(TokenBucketConfigurationProperties tokenBucketConfigurationProperties) {
        this.tokenBucketConfigurationProperties = tokenBucketConfigurationProperties;
    }

    @Override
    public RateLimiterAlgorithmResponse isAllowed(
        RateLimiterAlgorithmRequest request
    ) {
        int bucketCapacity = tokenBucketConfigurationProperties.getBucketCapacity();
        int refillRate = tokenBucketConfigurationProperties.getRefillRatePerSecond();
        log.info("Received request for key : {}, required tokens : {}", request.key(), request.tokensRequired());
        log.debug("Token bucket Config - bucketCapacity : {}, refillRatePerSecond : {}", bucketCapacity, refillRate);
        // fetch bucket if it is already present else create a new bucket with the full capacity
        BucketState bucket = tokenBucket.computeIfAbsent(request.key(), key ->
            new BucketState(bucketCapacity, System.nanoTime())
        );
        // updating bucket should be an atomic operation so wrapping it around synchronized block
        synchronized (bucket) {
            // current time in nanoseconds
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
                bucketCapacity,
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
                        refillRate
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
