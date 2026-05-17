package io.sriki.distributed_rate_limitter.model;

public record BucketCheckResult(boolean allowed, double remaining) {
}
