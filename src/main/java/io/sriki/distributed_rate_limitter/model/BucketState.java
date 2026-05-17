package io.sriki.distributed_rate_limitter.model;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public final class BucketState {

    private double remainingToken;
    private long lastRefillTimestampInNanos;

    public BucketState(double remainingToken, long lastRefillTimestampInNanos) {
        this.remainingToken = remainingToken;
        this.lastRefillTimestampInNanos = lastRefillTimestampInNanos;
    }

}
