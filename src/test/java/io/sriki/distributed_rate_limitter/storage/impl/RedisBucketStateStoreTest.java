package io.sriki.distributed_rate_limitter.storage.impl;

import io.sriki.distributed_rate_limitter.model.BucketCheckResult;
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
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
@ActiveProfiles("redis")
@Testcontainers
@EnabledIfSystemProperty(named = "docker.enabled", matches = "true")
class RedisBucketStateStoreTest {

    private static final long CAPACITY = 100L;
    private static final double REFILL_RATE = 10.0;
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
    private RedisBucketStateStore store;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        redisTemplate.getConnectionFactory().getConnection().flushAll();
    }

    @Test
    void firstConsumeStartsBucketAtCapacity() {
        BucketCheckResult result = store.tryConsume(KEY, 5, CAPACITY, REFILL_RATE);

        assertThat(result.allowed()).isTrue();
        assertThat(result.remaining()).isEqualTo(95.0, within(0.5));
    }

    @Test
    void successiveConsumesDeductTokens() {
        store.tryConsume(KEY, 30, CAPACITY, REFILL_RATE);
        BucketCheckResult second = store.tryConsume(KEY, 20, CAPACITY, REFILL_RATE);

        assertThat(second.allowed()).isTrue();
        assertThat(second.remaining()).isCloseTo(50.0, within(0.5));
    }

    @Test
    void deniesWhenInsufficientTokens() {
        store.tryConsume(KEY, 100, CAPACITY, REFILL_RATE);

        BucketCheckResult denied = store.tryConsume(KEY, 50, CAPACITY, REFILL_RATE);

        assertThat(denied.allowed()).isFalse();
        assertThat(denied.remaining()).isLessThan(50.0);
        assertThat(denied.remaining()).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void deniesWhenRequestExceedsCapacity() {
        BucketCheckResult denied = store.tryConsume(KEY, (int) CAPACITY + 1, CAPACITY, REFILL_RATE);

        assertThat(denied.allowed()).isFalse();
        assertThat(denied.remaining()).isLessThanOrEqualTo((double) CAPACITY);
    }

    @Test
    void refillsTokensOverTime() throws InterruptedException {
        double highRefill = 100.0;
        store.tryConsume("fast-key", (int) CAPACITY, CAPACITY, highRefill);
        Thread.sleep(150);

        BucketCheckResult afterWait = store.tryConsume("fast-key", 10, CAPACITY, highRefill);

        assertThat(afterWait.allowed()).isTrue();
        assertThat(afterWait.remaining()).isBetween(0.0, 20.0);
    }

    @Test
    void refillIsCappedAtCapacity() throws InterruptedException {
        store.tryConsume(KEY, 1, CAPACITY, REFILL_RATE);

        Thread.sleep(200);

        BucketCheckResult result = store.tryConsume(KEY, 1, CAPACITY, REFILL_RATE);

        assertThat(result.allowed()).isTrue();
        assertThat(result.remaining()).isLessThanOrEqualTo((double) CAPACITY - 1);
    }

    @Test
    void differentKeysAreIsolated() {
        store.tryConsume("client-a:/api", (int) CAPACITY, CAPACITY, REFILL_RATE);
        BucketCheckResult aDenied = store.tryConsume("client-a:/api", 10, CAPACITY, REFILL_RATE);
        assertThat(aDenied.allowed()).isFalse();

        BucketCheckResult bAllowed = store.tryConsume("client-b:/api", 10, CAPACITY, REFILL_RATE);
        assertThat(bAllowed.allowed()).isTrue();
        assertThat(bAllowed.remaining()).isCloseTo(90.0, within(0.5));
    }

    @Test
    void concurrentConsumesDoNotOverDrawBucket() throws InterruptedException {
        int threads = 200;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger allowed = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    BucketCheckResult result = store.tryConsume("hot-key", 1, CAPACITY, 1.0);
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

        assertThat(allowed.get()).isBetween((int) CAPACITY, (int) CAPACITY + 5);
    }
}
