package io.sriki.distributed_rate_limitter.algorithm.impl;

import io.sriki.distributed_rate_limitter.config.SlidingWindowConfigurationProperties;
import io.sriki.distributed_rate_limitter.model.RateLimiterAlgorithmRequest;
import io.sriki.distributed_rate_limitter.model.RateLimiterAlgorithmResponse;
import io.sriki.distributed_rate_limitter.model.SlidingWindowCheckResult;
import io.sriki.distributed_rate_limitter.storage.SlidingWindowStateStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlidingWindowImplementationTest {

    @Mock
    private SlidingWindowStateStore stateStore;

    @Mock
    private SlidingWindowConfigurationProperties config;

    @InjectMocks
    private SlidingWindowImplementation algorithm;

    private RateLimiterAlgorithmRequest request;

    @BeforeEach
    void setup() {
        request = new RateLimiterAlgorithmRequest("client1:/api/resource", 5);
        when(config.getWindowSizeInSeconds()).thenReturn(60);
        when(config.getMaxRequests()).thenReturn(100);
    }

    @Test
    void allowsRequestWhenWithinWindow() {
        when(stateStore.tryConsume(anyString(), anyInt(), anyInt(), anyInt()))
                .thenReturn(new SlidingWindowCheckResult(true, 5, 0));

        RateLimiterAlgorithmResponse response = algorithm.isAllowed(request);

        assertThat(response.allowed()).isTrue();
        assertThat(response.retryAfterMs()).isZero();
        assertThat(response.remainingToken()).isEqualTo(95);
    }

    @Test
    void deniesRequestWhenWindowExhausted() {
        when(stateStore.tryConsume(anyString(), anyInt(), anyInt(), anyInt()))
                .thenReturn(new SlidingWindowCheckResult(false, 100, 5000));

        RateLimiterAlgorithmResponse response = algorithm.isAllowed(request);

        assertThat(response.allowed()).isFalse();
        assertThat(response.remainingToken()).isZero();
    }

    @Test
    void calculatesRetryAfterFromOldestTimestamp() {
        long nowMs = Instant.now().toEpochMilli();
        long oldestMs = nowMs - 30_000; // 30s ago
        when(stateStore.tryConsume(anyString(), anyInt(), anyInt(), anyInt()))
                .thenReturn(new SlidingWindowCheckResult(false, 100, oldestMs));

        RateLimiterAlgorithmResponse response = algorithm.isAllowed(request);

        assertThat(response.allowed()).isFalse();
        // oldest + 60s window = oldestMs + 60000, minus nowMs ≈ 30000
        assertThat(response.retryAfterMs()).isBetween(29_000L, 31_000L);
    }

    @Test
    void reportsRemainingAsMaxRequestsMinusCurrentCount() {
        when(stateStore.tryConsume(anyString(), anyInt(), anyInt(), anyInt()))
                .thenReturn(new SlidingWindowCheckResult(true, 30, 0));

        RateLimiterAlgorithmResponse response = algorithm.isAllowed(request);

        assertThat(response.remainingToken()).isEqualTo(70);
    }

    @Test
    void remainingIsNeverNegative() {
        when(stateStore.tryConsume(anyString(), anyInt(), anyInt(), anyInt()))
                .thenReturn(new SlidingWindowCheckResult(false, 150, 1000));

        RateLimiterAlgorithmResponse response = algorithm.isAllowed(request);

        assertThat(response.remainingToken()).isZero();
    }
}
