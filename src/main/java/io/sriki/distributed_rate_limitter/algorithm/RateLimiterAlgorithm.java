package io.sriki.distributed_rate_limitter.algorithm;

import io.sriki.distributed_rate_limitter.model.RateLimiterAlgorithmRequest;
import io.sriki.distributed_rate_limitter.model.RateLimiterAlgorithmResponse;

public interface RateLimiterAlgorithm {
    RateLimiterAlgorithmResponse isAllowed(RateLimiterAlgorithmRequest request);
}
