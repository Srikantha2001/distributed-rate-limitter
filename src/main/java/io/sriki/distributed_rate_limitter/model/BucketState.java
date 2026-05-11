package io.sriki.distributed_rate_limitter.model;

public final class BucketState {

    private double remainingToken;
    private long lastRefillTimestampInNanos;

    public BucketState(double remainingToken, long lastRefillTimestampInNanos) {
        this.remainingToken = remainingToken;
        this.lastRefillTimestampInNanos = lastRefillTimestampInNanos;
    }

    public void setRemainingToken(double remainingToken) {
        this.remainingToken = remainingToken;
    }

    public double getRemainingToken() {
        return this.remainingToken;
    }

    public void setLastRefillTimestampInNanos(long lastRefillTimestampInNanos) {
        this.lastRefillTimestampInNanos = lastRefillTimestampInNanos;
    }

    public long getLastRefillTimestampInNanos() {
        return this.lastRefillTimestampInNanos;
    }
}
