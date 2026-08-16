package io.sriki.distributed_rate_limitter.algorithm.impl;

import io.sriki.distributed_rate_limitter.algorithm.RateLimiterAlgorithm;
import io.sriki.distributed_rate_limitter.config.SlidingWindowConfigurationProperties;
import io.sriki.distributed_rate_limitter.model.RateLimiterAlgorithmRequest;
import io.sriki.distributed_rate_limitter.model.RateLimiterAlgorithmResponse;
import io.sriki.distributed_rate_limitter.model.SlidingWindowCheckResult;
import io.sriki.distributed_rate_limitter.storage.SlidingWindowStateStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@ConditionalOnProperty(name = "rate-limiter.algorithm", havingValue = "sliding-window")
@Slf4j
public class SlidingWindowImplementation implements RateLimiterAlgorithm {

    private final SlidingWindowConfigurationProperties config;
    private final SlidingWindowStateStore stateStore;

    public SlidingWindowImplementation(SlidingWindowConfigurationProperties config, SlidingWindowStateStore stateStore) {
        this.config = config;
        this.stateStore = stateStore;
    }

    @Override
    public RateLimiterAlgorithmResponse isAllowed(RateLimiterAlgorithmRequest request) {
        int windowSizeInSeconds = config.getWindowSizeInSeconds();
        int maxRequests = config.getMaxRequests();

        log.debug("Received request for key : {}, required tokens : {}", request.key(), request.tokensRequired());

        SlidingWindowCheckResult result = stateStore.tryConsume(
                request.key(),
                request.tokensRequired(),
                windowSizeInSeconds,
                maxRequests
        );

        long remaining = Math.max(0, maxRequests - result.currentCount());

        if (result.allowed()) {
            log.debug("Request allowed for key : {}, remaining tokens : {}", request.key(), remaining);
            return new RateLimiterAlgorithmResponse(true, remaining, 0);
        } else {
            long nowMs = Instant.now().toEpochMilli();
            long retryAfterMs = Math.max(0, result.oldestRequestTimestampMs() + (windowSizeInSeconds * 1000L) - nowMs);
            log.debug("Request denied for key : {}, remaining tokens : {}, retry after (ms) : {}", request.key(), remaining, retryAfterMs);
            return new RateLimiterAlgorithmResponse(false, remaining, retryAfterMs);
        }
    }
}
