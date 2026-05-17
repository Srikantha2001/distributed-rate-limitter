package io.sriki.distributed_rate_limitter.algorithm.impl;

import io.sriki.distributed_rate_limitter.config.TokenBucketConfigurationProperties;
import io.sriki.distributed_rate_limitter.model.BucketCheckResult;
import io.sriki.distributed_rate_limitter.model.RateLimiterAlgorithmRequest;
import io.sriki.distributed_rate_limitter.model.RateLimiterAlgorithmResponse;
import io.sriki.distributed_rate_limitter.storage.BucketStateStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SpringBootTest
@ActiveProfiles("test")
class TokenBucketImplementationTest {

    @Autowired
    private TokenBucketConfigurationProperties tokenBucketConfigurationProperties;

    @Mock
    private BucketStateStore bucketStateStore;

    @InjectMocks
    private TokenBucketImplementation tokenBucketImplementation;
    private RateLimiterAlgorithmRequest request;

    @BeforeEach
    void setup() {
        request = new RateLimiterAlgorithmRequest("client1:/api/resource", 5);
        tokenBucketImplementation = new TokenBucketImplementation(tokenBucketConfigurationProperties, bucketStateStore);
    }

    @Test
    void shouldBindToCorrectTokenBucketConfigurationProperties() {
        assertThat(tokenBucketConfigurationProperties.getBucketCapacity()).isEqualTo(100);
        assertThat(tokenBucketConfigurationProperties.getRefillRatePerSecond()).isEqualTo(10);
    }

    @Test
    void firstRequestStartsWithFullBucket() {
        when(bucketStateStore.tryConsume(request.key(), request.tokensRequired(), tokenBucketConfigurationProperties.getBucketCapacity(), tokenBucketConfigurationProperties.getRefillRatePerSecond()))
                .thenReturn(new BucketCheckResult(true, 95.0));
        RateLimiterAlgorithmResponse response = tokenBucketImplementation.isAllowed(request);

        assertThat(response.allowed()).isTrue();
        assertThat(response.remainingToken()).isEqualTo(95.0, within(0.001));
        assertThat(response.retryAfterMs()).isZero();
    }

    @Test
    void deniesRequestWhenInsufficientTokensAvailable() {
        String key = "drained-client:/api/resouce";

        when(bucketStateStore.tryConsume(key, 100, tokenBucketConfigurationProperties.getBucketCapacity(), tokenBucketConfigurationProperties.getRefillRatePerSecond()))
                .thenReturn(new BucketCheckResult(true, 0))
                .thenReturn(new BucketCheckResult(false, 0));

        tokenBucketImplementation.isAllowed(new RateLimiterAlgorithmRequest(key, 100));

        RateLimiterAlgorithmResponse response = tokenBucketImplementation.isAllowed(
                new RateLimiterAlgorithmRequest(key, 100)
        );

        assertThat(response.allowed()).isFalse();
        assertThat(response.retryAfterMs()).isGreaterThan(0);
    }

    @Test
    void deniesRequestExceedingBucketCapacity() {
        String key = "oversize-client:/api/resource";
        when(bucketStateStore.tryConsume(key, 101, tokenBucketConfigurationProperties.getBucketCapacity(), tokenBucketConfigurationProperties.getRefillRatePerSecond()))
                .thenReturn(new BucketCheckResult(false, 0));
        RateLimiterAlgorithmResponse response = tokenBucketImplementation.isAllowed(
                new RateLimiterAlgorithmRequest(key, 101)
        );

        assertThat(response.allowed()).isFalse();
        assertThat(response.retryAfterMs()).isGreaterThan(0);
    }

    @Test
    void calculatesRetryAfterFromShortfallAndRefillRate() {
        String key = "retry-client:/api/resource";
        when(bucketStateStore.tryConsume(key, 100, tokenBucketConfigurationProperties.getBucketCapacity(), tokenBucketConfigurationProperties.getRefillRatePerSecond()))
                .thenReturn(new BucketCheckResult(true, 0));
        when(bucketStateStore.tryConsume(key, 10, tokenBucketConfigurationProperties.getBucketCapacity(), tokenBucketConfigurationProperties.getRefillRatePerSecond()))
                .thenReturn(new BucketCheckResult(false, 0));

        tokenBucketImplementation.isAllowed(new RateLimiterAlgorithmRequest(key, 100));

        // immediately ask for 10 more tokens; at 10 tokens/sec the wait is ~1s (1e9 ns).
        RateLimiterAlgorithmResponse response = tokenBucketImplementation.isAllowed(
                new RateLimiterAlgorithmRequest(key, 10)
        );

        assertThat(response.allowed()).isFalse();
        // a tiny refill may shave off a few ms between the two calls — allow a small window.
        assertThat(response.retryAfterMs()).isBetween(900L, 1000L);
    }
}