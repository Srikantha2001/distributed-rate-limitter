package io.sriki.distributed_rate_limitter.model;

public record SlidingWindowCheckResult(boolean allowed, long currentCount, long oldestRequestTimestampMs) {
}
