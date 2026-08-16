package io.sriki.distributed_rate_limitter.algorithm.impl;

import io.sriki.distributed_rate_limitter.algorithm.RateLimiterAlgorithm;
import io.sriki.distributed_rate_limitter.config.TokenBucketConfigurationProperties;
import io.sriki.distributed_rate_limitter.model.BucketCheckResult;
import io.sriki.distributed_rate_limitter.model.RateLimiterAlgorithmRequest;
import io.sriki.distributed_rate_limitter.model.RateLimiterAlgorithmResponse;
import io.sriki.distributed_rate_limitter.storage.BucketStateStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "rate-limiter.algorithm", havingValue = "token-bucket", matchIfMissing = true)
@Slf4j
public class TokenBucketImplementation implements RateLimiterAlgorithm {

    private final TokenBucketConfigurationProperties tokenBucketConfigurationProperties;
    private final BucketStateStore bucketStateStore;

    public TokenBucketImplementation(TokenBucketConfigurationProperties tokenBucketConfigurationProperties,
                                      BucketStateStore bucketStateStore) {
        this.tokenBucketConfigurationProperties = tokenBucketConfigurationProperties;
        this.bucketStateStore = bucketStateStore;
    }

    @Override
    public RateLimiterAlgorithmResponse isAllowed(
            RateLimiterAlgorithmRequest request
    ) {
        int bucketCapacity = tokenBucketConfigurationProperties.getBucketCapacity();
        double refillRate = tokenBucketConfigurationProperties.getRefillRatePerSecond();
        log.debug("Received request for key : {}, required tokens : {}", request.key(), request.tokensRequired());

        BucketCheckResult result = bucketStateStore.tryConsume(
                request.key(),
                request.tokensRequired(),
                bucketCapacity,
                refillRate
        );

        if (result.allowed()) {
            log.debug("Request allowed for key : {}, remaining tokens : {}", request.key(), result.remaining());
            return new RateLimiterAlgorithmResponse(
                    true,
                    result.remaining(),
                    0
            );
        } else {
            long retryAfterMs = getRetryAfterTimeInMs(result.remaining(), request.tokensRequired(), refillRate);
            log.debug("Request denied for key : {}, remaining tokens : {}, retry after (ms) : {}", request.key(), result.remaining(), retryAfterMs);
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
            double refill_rate
    ) {
        return (long) Math.ceil(((requiredTokens - currentTokens) * 1000) /
                refill_rate);
    }
}
