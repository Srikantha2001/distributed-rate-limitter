package io.sriki.distributed_rate_limitter.storage;

import io.sriki.distributed_rate_limitter.model.BucketCheckResult;

public interface BucketStateStore {
    BucketCheckResult tryConsume(String key, int tokens, long capacity, double refillRate);
}
