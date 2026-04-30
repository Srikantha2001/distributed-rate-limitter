package io.sriki.distributed_rate_limitter.algorithm.impl;

import io.sriki.distributed_rate_limitter.algorithm.RateLimiterAlgorithm;
import io.sriki.distributed_rate_limitter.model.RateLimiterAlgorithmRequest;
import io.sriki.distributed_rate_limitter.model.RateLimiterAlgorithmResponse;
import org.springframework.stereotype.Service;

@Service
public class TokenBucketImplementation implements RateLimiterAlgorithm {

    @Override
    public RateLimiterAlgorithmResponse isAllowed(
        RateLimiterAlgorithmRequest request
    ) {
        throw new UnsupportedOperationException(
            "Unimplemented method 'isAllowed'"
        );
    }
}
