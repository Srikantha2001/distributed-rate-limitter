package io.sriki.distributed_rate_limitter.storage.impl;

import io.sriki.distributed_rate_limitter.model.SlidingWindowCheckResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("redis")
@Testcontainers
@EnabledIfSystemProperty(named = "docker.enabled", matches = "true")
class RedisSlidingWindowStateStoreTest {

    private static final int WINDOW_SIZE = 60;
    private static final int MAX_REQUESTS = 100;
    private static final String KEY = "client1:/api/resource";

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private RedisSlidingWindowStateStore store;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        redisTemplate.getConnectionFactory().getConnection().flushAll();
    }

    @Test
    void firstRequestIsAllowed() {
        SlidingWindowCheckResult result = store.tryConsume(KEY, 5, WINDOW_SIZE, MAX_REQUESTS);

        assertThat(result.allowed()).isTrue();
        assertThat(result.currentCount()).isEqualTo(5);
    }

    @Test
    void consumesTokensUpToMax() {
        SlidingWindowCheckResult first = store.tryConsume(KEY, 60, WINDOW_SIZE, MAX_REQUESTS);
        assertThat(first.allowed()).isTrue();
        assertThat(first.currentCount()).isEqualTo(60);

        SlidingWindowCheckResult second = store.tryConsume(KEY, 40, WINDOW_SIZE, MAX_REQUESTS);
        assertThat(second.allowed()).isTrue();
        assertThat(second.currentCount()).isEqualTo(100);
    }

    @Test
    void deniesWhenWindowIsFull() {
        store.tryConsume(KEY, 100, WINDOW_SIZE, MAX_REQUESTS);

        SlidingWindowCheckResult result = store.tryConsume(KEY, 1, WINDOW_SIZE, MAX_REQUESTS);

        assertThat(result.allowed()).isFalse();
        assertThat(result.currentCount()).isEqualTo(100);
    }

    @Test
    void deniesWhenSingleRequestExceedsMax() {
        SlidingWindowCheckResult result = store.tryConsume(KEY, 101, WINDOW_SIZE, MAX_REQUESTS);

        assertThat(result.allowed()).isFalse();
        assertThat(result.currentCount()).isZero();
    }

    @Test
    void oldestTimestampIsSetOnDenial() {
        store.tryConsume(KEY, 100, WINDOW_SIZE, MAX_REQUESTS);

        SlidingWindowCheckResult result = store.tryConsume(KEY, 1, WINDOW_SIZE, MAX_REQUESTS);

        assertThat(result.allowed()).isFalse();
        assertThat(result.oldestRequestTimestampMs()).isGreaterThan(0);
    }

    @Test
    void differentKeysAreIsolated() {
        store.tryConsume("client-a:/api", 100, WINDOW_SIZE, MAX_REQUESTS);
        SlidingWindowCheckResult aDenied = store.tryConsume("client-a:/api", 1, WINDOW_SIZE, MAX_REQUESTS);
        assertThat(aDenied.allowed()).isFalse();

        SlidingWindowCheckResult bAllowed = store.tryConsume("client-b:/api", 50, WINDOW_SIZE, MAX_REQUESTS);
        assertThat(bAllowed.allowed()).isTrue();
        assertThat(bAllowed.currentCount()).isEqualTo(50);
    }

    @Test
    void windowSlidesOverTime() throws InterruptedException {
        int shortWindow = 1;
        int maxPerSecond = 5;

        for (int i = 0; i < 5; i++) {
            SlidingWindowCheckResult r = store.tryConsume(KEY, 1, shortWindow, maxPerSecond);
            assertThat(r.allowed()).isTrue();
        }

        SlidingWindowCheckResult denied = store.tryConsume(KEY, 1, shortWindow, maxPerSecond);
        assertThat(denied.allowed()).isFalse();

        Thread.sleep(1100);

        SlidingWindowCheckResult afterWait = store.tryConsume(KEY, 1, shortWindow, maxPerSecond);
        assertThat(afterWait.allowed()).isTrue();
        assertThat(afterWait.currentCount()).isEqualTo(1);
    }

    @Test
    void concurrentConsumesDoNotExceedWindowLimit() throws InterruptedException {
        int max = 50;
        int threads = 200;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger allowed = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    SlidingWindowCheckResult result = store.tryConsume("hot-key", 1, 60, max);
                    if (result.allowed()) {
                        allowed.incrementAndGet();
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
        pool.shutdownNow();

        assertThat(allowed.get()).isEqualTo(max);
    }

    @Test
    void zeroTokensDoesNotAddToWindow() {
        SlidingWindowCheckResult result = store.tryConsume(KEY, 0, WINDOW_SIZE, MAX_REQUESTS);

        assertThat(result.allowed()).isTrue();
        assertThat(result.currentCount()).isZero();
    }
}
