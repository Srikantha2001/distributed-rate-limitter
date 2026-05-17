package io.sriki.distributed_rate_limitter.algorithm.impl;

import io.sriki.distributed_rate_limitter.algorithm.RateLimiterAlgorithm;
import io.sriki.distributed_rate_limitter.config.TokenBucketConfigurationProperties;
import io.sriki.distributed_rate_limitter.model.BucketCheckResult;
import io.sriki.distributed_rate_limitter.model.RateLimiterAlgorithmRequest;
import io.sriki.distributed_rate_limitter.model.RateLimiterAlgorithmResponse;
import io.sriki.distributed_rate_limitter.storage.BucketStateStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class TokenBucketImplementation implements RateLimiterAlgorithm {

    private final TokenBucketConfigurationProperties tokenBucketConfigurationProperties;
    private final BucketStateStore bucketStateStore;

    public TokenBucketImplementation(TokenBucketConfigurationProperties tokenBucketConfigurationProperties,
                                     @Qualifier("inMemoryBucketStateStore") BucketStateStore bucketStateStore) {
        this.tokenBucketConfigurationProperties = tokenBucketConfigurationProperties;
        this.bucketStateStore = bucketStateStore;
    }

    @Override
    public RateLimiterAlgorithmResponse isAllowed(
            RateLimiterAlgorithmRequest request
    ) {
        int bucketCapacity = tokenBucketConfigurationProperties.getBucketCapacity();
        int refillRate = tokenBucketConfigurationProperties.getRefillRatePerSecond();
        log.info("Received request for key : {}, required tokens : {}", request.key(), request.tokensRequired());

        BucketCheckResult result = bucketStateStore.tryConsume(
                request.key(),
                request.tokensRequired(),
                bucketCapacity,
                refillRate
        );

        if (result.allowed()) {
            log.info("Request allowed for key : {}, remaining tokens : {}", request.key(), result.remaining());
            return new RateLimiterAlgorithmResponse(
                    true,
                    result.remaining(),
                    0
            );
        } else {
            long retryAfterMs = getRetryAfterTimeInMs(result.remaining(), request.tokensRequired(), refillRate);
            log.info("Request denied for key : {}, remaining tokens : {}, retry after (ms) : {}", request.key(), result.remaining(), retryAfterMs);
            return new RateLimiterAlgorithmResponse(
                    false,
                    result.remaining(),
                    retryAfterMs
            );
        }
    }

    private long getRetryAfterTimeInMs(
            double currentTokens,
            double requiredTokens,
            long refill_rate
    ) {
        return (long) (((requiredTokens - currentTokens) * Math.pow(10, 3)) /
                refill_rate);
    }
}
