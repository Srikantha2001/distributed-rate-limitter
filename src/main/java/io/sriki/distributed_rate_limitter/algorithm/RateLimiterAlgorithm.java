package io.sriki.distributed_rate_limitter.algorithm;

public interface RateLimiterAlgorithm {
    boolean isAllowed(String clientId, String resource);
}
