package io.sriki.distributed_rate_limitter.algorithm.impl;

import io.sriki.distributed_rate_limitter.algorithm.RateLimiterAlgorithm;
import io.sriki.distributed_rate_limitter.model.RateLimiterAlgorithmRequest;
import io.sriki.distributed_rate_limitter.model.RateLimiterAlgorithmResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles({"test", "test-sliding-window"})
class SlidingWindowImplementationContextTest {

    @Autowired
    private RateLimiterAlgorithm algorithm;

    @Test
    void slidingWindowAlgorithmIsWired() {
        assertThat(algorithm).isInstanceOf(SlidingWindowImplementation.class);
    }

    @Test
    void allowsRequestsWithinWindow() {
        for (int i = 0; i < 5; i++) {
            RateLimiterAlgorithmResponse response = algorithm.isAllowed(
                    new RateLimiterAlgorithmRequest("ctx-test:/api", 1)
            );
            assertThat(response.allowed()).isTrue();
            assertThat(response.remainingToken()).isEqualTo(5 - i - 1);
            assertThat(response.retryAfterMs()).isZero();
        }
    }

    @Test
    void deniesRequestWhenWindowExhausted() {
        for (int i = 0; i < 5; i++) {
            algorithm.isAllowed(new RateLimiterAlgorithmRequest("ctx-deny:/api", 1));
        }

        RateLimiterAlgorithmResponse response = algorithm.isAllowed(
                new RateLimiterAlgorithmRequest("ctx-deny:/api", 1)
        );

        assertThat(response.allowed()).isFalse();
        assertThat(response.retryAfterMs()).isGreaterThan(0);
    }

    @Test
    void differentKeysAreIsolated() {
        for (int i = 0; i < 5; i++) {
            algorithm.isAllowed(new RateLimiterAlgorithmRequest("ctx-a:/api", 1));
        }

        RateLimiterAlgorithmResponse aDenied = algorithm.isAllowed(
                new RateLimiterAlgorithmRequest("ctx-a:/api", 1)
        );
        assertThat(aDenied.allowed()).isFalse();

        RateLimiterAlgorithmResponse bAllowed = algorithm.isAllowed(
                new RateLimiterAlgorithmRequest("ctx-b:/api", 1)
        );
        assertThat(bAllowed.allowed()).isTrue();
    }

    @Test
    void tokensWeightConsumesMultipleSlots() {
        RateLimiterAlgorithmResponse first = algorithm.isAllowed(
                new RateLimiterAlgorithmRequest("ctx-weighted:/api", 5)
        );
        assertThat(first.allowed()).isTrue();
        assertThat(first.remainingToken()).isZero();

        RateLimiterAlgorithmResponse denied = algorithm.isAllowed(
                new RateLimiterAlgorithmRequest("ctx-weighted:/api", 1)
        );
        assertThat(denied.allowed()).isFalse();
    }
}
