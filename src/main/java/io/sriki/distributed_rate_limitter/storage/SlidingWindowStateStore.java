package io.sriki.distributed_rate_limitter.storage;

import io.sriki.distributed_rate_limitter.model.SlidingWindowCheckResult;

public interface SlidingWindowStateStore {
    SlidingWindowCheckResult tryConsume(String key, int tokens, int windowSizeInSeconds, int maxRequests);
}
