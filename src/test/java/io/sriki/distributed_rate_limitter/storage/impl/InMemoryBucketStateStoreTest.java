package io.sriki.distributed_rate_limitter.storage.impl;

import io.sriki.distributed_rate_limitter.model.BucketCheckResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class InMemoryBucketStateStoreTest {

    private static final long CAPACITY = 100L;
    private static final double REFILL_RATE = 10.0;
    private static final String KEY = "client1:/api/resource";

    private InMemoryBucketStateStore store;

    @BeforeEach
    void setup() {
        store = new InMemoryBucketStateStore();
    }

    @Test
    void firstConsumeStartsBucketAtCapacity() {
        BucketCheckResult result = store.tryConsume(KEY, 5, CAPACITY, REFILL_RATE);

        assertThat(result.allowed()).isTrue();
        // bucket initializes at capacity; refill between init and consume is negligible (capped at capacity).
        assertThat(result.remaining()).isEqualTo(95.0, within(0.01));
    }

    @Test
    void successiveConsumesDeductTokens() {
        store.tryConsume(KEY, 30, CAPACITY, REFILL_RATE);
        BucketCheckResult second = store.tryConsume(KEY, 20, CAPACITY, REFILL_RATE);

        assertThat(second.allowed()).isTrue();
        // 100 - 30 - 20 = 50, give a small tolerance for accrued refill between calls.
        assertThat(second.remaining()).isCloseTo(50.0, within(0.1));
    }

    @Test
    void deniesWhenInsufficientTokens() {
        store.tryConsume(KEY, 100, CAPACITY, REFILL_RATE);

        BucketCheckResult denied = store.tryConsume(KEY, 50, CAPACITY, REFILL_RATE);

        assertThat(denied.allowed()).isFalse();
        // denial leaves the (refill-adjusted) balance in place; should be tiny and well under the request.
        assertThat(denied.remaining()).isLessThan(50.0);
        assertThat(denied.remaining()).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void deniesWhenRequestExceedsCapacity() {
        BucketCheckResult denied = store.tryConsume(KEY, (int) CAPACITY + 1, CAPACITY, REFILL_RATE);

        assertThat(denied.allowed()).isFalse();
        // remaining is the refilled-but-not-consumed amount, capped at capacity.
        assertThat(denied.remaining()).isLessThanOrEqualTo((double) CAPACITY);
    }

    @Test
    void refillsTokensOverTime() throws InterruptedException {
        // drain the bucket completely.
        BucketCheckResult drained = store.tryConsume(KEY, (int) CAPACITY, CAPACITY, REFILL_RATE);
        assertThat(drained.allowed()).isTrue();

        // at 100 tokens/sec, ~150ms should refill ~15 tokens.
        double highRefill = 100.0;
        store.tryConsume("fast-key", (int) CAPACITY, CAPACITY, highRefill);
        Thread.sleep(150);

        BucketCheckResult afterWait = store.tryConsume("fast-key", 10, CAPACITY, highRefill);

        assertThat(afterWait.allowed()).isTrue();
        // refilled ~15, consumed 10 → ~5 remaining. Allow generous tolerance for scheduling jitter.
        assertThat(afterWait.remaining()).isBetween(0.0, 20.0);
    }

    @Test
    void refillIsCappedAtCapacity() throws InterruptedException {
        // first consume initializes the bucket at capacity.
        store.tryConsume(KEY, 1, CAPACITY, REFILL_RATE);

        // sleep far longer than it would take to refill to capacity (100 / 10 = 10s).
        Thread.sleep(200);

        BucketCheckResult result = store.tryConsume(KEY, 1, CAPACITY, REFILL_RATE);

        assertThat(result.allowed()).isTrue();
        // even with the long wait, remaining must never exceed capacity - 1.
        assertThat(result.remaining()).isLessThanOrEqualTo((double) CAPACITY - 1);
    }

    @Test
    void differentKeysAreIsolated() {
        // drain key A.
        store.tryConsume("client-a:/api", (int) CAPACITY, CAPACITY, REFILL_RATE);
        BucketCheckResult aDenied = store.tryConsume("client-a:/api", 10, CAPACITY, REFILL_RATE);
        assertThat(aDenied.allowed()).isFalse();

        // key B should still have a full bucket.
        BucketCheckResult bAllowed = store.tryConsume("client-b:/api", 10, CAPACITY, REFILL_RATE);
        assertThat(bAllowed.allowed()).isTrue();
        assertThat(bAllowed.remaining()).isCloseTo(90.0, within(0.1));
    }

    @Test
    void concurrentConsumesDoNotOverDrawBucket() throws InterruptedException {
        // 200 threads each try to take 1 token from a bucket of capacity 100.
        // with a tiny refill rate, almost no tokens are added during the test window,
        // so the number of successful consumes must be ~capacity (allow small slack for refill).
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

        // at refill rate 1/sec, expect at most capacity + a handful of refilled tokens.
        assertThat(allowed.get()).isBetween((int) CAPACITY, (int) CAPACITY + 5);
    }
}