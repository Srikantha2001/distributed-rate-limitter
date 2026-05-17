package io.sriki.distributed_rate_limitter.algorithm.impl;

import io.sriki.distributed_rate_limitter.config.TokenBucketConfigurationProperties;
import io.sriki.distributed_rate_limitter.model.RateLimiterAlgorithmRequest;
import io.sriki.distributed_rate_limitter.model.RateLimiterAlgorithmResponse;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@ExtendWith(MockitoExtension.class)
@SpringBootTest
@ActiveProfiles("test")
class TokenBucketImplementationTest {

    @Autowired
    private TokenBucketConfigurationProperties tokenBucketConfigurationProperties;

    private TokenBucketImplementation tokenBucketImplementation;
    private RateLimiterAlgorithmRequest request;

    @BeforeEach
    void setup() {
        request = new RateLimiterAlgorithmRequest("client1:/api/resource", 5);
        tokenBucketImplementation = new TokenBucketImplementation(tokenBucketConfigurationProperties);
    }

    @Test
    void shouldBindToCorrectTokenBucketConfigurationProperties() {
        assertThat(tokenBucketConfigurationProperties.getBucketCapacity()).isEqualTo(100);
        assertThat(tokenBucketConfigurationProperties.getRefillRatePerSecond()).isEqualTo(10);
    }

    @Test
    void firstRequestStartsWithFullBucket() {
        RateLimiterAlgorithmResponse response = tokenBucketImplementation.isAllowed(request);

        assertThat(response.allowed()).isTrue();
        assertThat(response.remainingToken()).isEqualTo(95.0, within(0.001));
        assertThat(response.retryAfterNanos()).isZero();
    }

    @Test
    void consumesTokensAcrossSequentialRequests() {
        for (int i = 0; i < 20; i++) {
            RateLimiterAlgorithmResponse response = tokenBucketImplementation.isAllowed(request);
            assertThat(response.allowed())
                .as("request #%d (5 tokens each, capacity 100) should be allowed", i + 1)
                .isTrue();
        }
    }

    @Test
    void deniesRequestWhenInsufficientTokensAvailable() {
        String key = "drained-client:/api/resouce";
        tokenBucketImplementation.isAllowed(new RateLimiterAlgorithmRequest(key, 100));

        RateLimiterAlgorithmResponse response = tokenBucketImplementation.isAllowed(
            new RateLimiterAlgorithmRequest(key, 5)
        );

        assertThat(response.allowed()).isFalse();
        assertThat(response.retryAfterNanos()).isGreaterThan(0);
    }

    @Test
    void deniesRequestExceedingBucketCapacity() {
        RateLimiterAlgorithmResponse response = tokenBucketImplementation.isAllowed(
            new RateLimiterAlgorithmRequest("oversize-client:/api/resource", 101)
        );

        assertThat(response.allowed()).isFalse();
        assertThat(response.retryAfterNanos()).isGreaterThan(0);
    }

    @Test
    void allowsRequestExactlyAtCapacity() {
        RateLimiterAlgorithmResponse response = tokenBucketImplementation.isAllowed(
            new RateLimiterAlgorithmRequest("at-capacity-client:/api/resource", 100)
        );

        assertThat(response.allowed()).isTrue();
        assertThat(response.remainingToken()).isEqualTo(0.0, within(0.001));
        assertThat(response.retryAfterNanos()).isZero();
    }

    @Test
    void calculatesRetryAfterFromShortfallAndRefillRate() {
        String key = "retry-client:/api/resource";
        tokenBucketImplementation.isAllowed(new RateLimiterAlgorithmRequest(key, 100));

        // immediately ask for 10 more tokens; at 10 tokens/sec the wait is ~1s (1e9 ns).
        RateLimiterAlgorithmResponse response = tokenBucketImplementation.isAllowed(
            new RateLimiterAlgorithmRequest(key, 10)
        );

        assertThat(response.allowed()).isFalse();
        // a tiny refill may shave off a few ms between the two calls — allow a small window.
        assertThat(response.retryAfterNanos()).isBetween(900_000_000L, 1_000_000_000L);
    }

    @Test
    void refillsTokensAfterTimePasses() throws InterruptedException {
        String key = "refill-client:/api/resource";
        RateLimiterAlgorithmResponse drained = tokenBucketImplementation.isAllowed(
            new RateLimiterAlgorithmRequest(key, 100)
        );
        assertThat(drained.allowed()).isTrue();

        // 10 tokens/sec * 0.3s ≈ 3 tokens refilled
        Thread.sleep(300);

        RateLimiterAlgorithmResponse response = tokenBucketImplementation.isAllowed(
            new RateLimiterAlgorithmRequest(key, 2)
        );
        assertThat(response.allowed()).isTrue();
    }

    @Test
    void capsRefillAtBucketCapacity() throws InterruptedException {
        String key = "cap-client:/api/resource";
        // create the bucket nearly full
        tokenBucketImplementation.isAllowed(new RateLimiterAlgorithmRequest(key, 1));

        // wait long enough that uncapped refill would push past capacity
        Thread.sleep(300);

        // bucket should still hold no more than `bucketCapacity` tokens
        RateLimiterAlgorithmResponse response = tokenBucketImplementation.isAllowed(
            new RateLimiterAlgorithmRequest(key, 100)
        );
        assertThat(response.allowed()).isTrue();
        assertThat(response.remainingToken()).isLessThanOrEqualTo(1.0);
    }

    @Test
    void tracksBucketsForEachKeyIndependently() {
        tokenBucketImplementation.isAllowed(new RateLimiterAlgorithmRequest("keyA:/api/resource", 100));

        RateLimiterAlgorithmResponse responseB = tokenBucketImplementation.isAllowed(
            new RateLimiterAlgorithmRequest("keyB:/api/resource", 50)
        );

        assertThat(responseB.allowed()).isTrue();
        assertThat(responseB.remainingToken()).isEqualTo(50.0, within(0.001));
    }

    @RepeatedTest(5)
    void doesNotOverAllocateUnderConcurrentLoad() throws InterruptedException {
        int threads = 200;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger allowedCount = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    start.await();
                    RateLimiterAlgorithmResponse response = tokenBucketImplementation.isAllowed(
                        new RateLimiterAlgorithmRequest("concurrent-client:/api/recources", 1)
                    );
                    if (response.allowed()) {
                        allowedCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        // capacity is 100; refill during the burst is sub-token, so the bucket
        // must never grant more than ~capacity requests.
        assertThat(allowedCount.get()).isBetween(100, 105);
    }
}