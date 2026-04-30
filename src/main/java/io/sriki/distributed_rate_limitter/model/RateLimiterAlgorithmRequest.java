package io.sriki.distributed_rate_limitter.model;

public record RateLimiterAlgorithmRequest(String key, int tokensRequired) {}
