package io.sriki.distributed_rate_limitter.model;

public record RateLimiterAlgorithmResponse(
    boolean allowed,
    double remainingToken,
    long retryAfterMs
) {}
