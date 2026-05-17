# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Build and run all tests
mvn -B verify

# Run tests only
mvn test

# Run a single test class / method
mvn test -Dtest=DistributedRateLimitterApplicationTests
mvn test -Dtest=DistributedRateLimitterApplicationTests#contextLoads

# Build without tests
mvn -B package -DskipTests

# Run the application
mvn spring-boot:run
```

Proto sources are generated automatically during the build by `protobuf-maven-plugin` into `target/generated-sources/`.

## Toolchain

- **JDK 25 is required.** `pom.xml` pins `maven.compiler.source/target` to 25; CI uses `temurin-25`. JDK 21 will not compile.
- **Spring Boot 4.1.0-SNAPSHOT.** `pom.xml` adds the `spring-snapshots` repo (`repo.spring.io/snapshot`) — the first build needs network access to it.

## Architecture

A **Spring Boot gRPC server** that implements a distributed rate limiter. Currently single-node with an in-memory store; Redis-backed shared state is planned. See `docs/architecture.md` for the intended design before making structural changes.

**Layers (request flows top-to-bottom):**

- `src/main/proto/rate_limiter.proto` — gRPC contract. Proto package `io.sriki.ratelimiter.v1`; generated Java lands in `io.sriki.ratelimiter.proto` (the proto package and the Java package are deliberately different).
- `grpc/RateLimiterServiceImpl` — `@GrpcService` extending the generated `RateLimiterServiceGrpc.RateLimiterServiceImplBase`. Validates the request (`clientId` non-empty + alphanumeric; `resource` non-empty), builds the storage key as `clientId:resource`, defaults `tokens` to `1` if `< 1`, and delegates to a `RateLimiterAlgorithm`. Maps the algorithm response (allowed / remaining / retryAfterMs) into `CheckRateLimitResponse`. Invalid input is surfaced as `Status.INVALID_ARGUMENT`.
- `algorithm/RateLimiterAlgorithm` — strategy interface (`isAllowed(RateLimiterAlgorithmRequest) → RateLimiterAlgorithmResponse`) for pluggable algorithms.
- `algorithm/impl/TokenBucketImplementation` — current `@Service` impl. Reads `bucketCapacity` / `refillRatePerSecond` from `TokenBucketConfigurationProperties`, delegates the actual consume to the injected `BucketStateStore` (qualifier `inMemoryBucketStateStore`), and on denial computes `retryAfterMs = (tokensRequired - remaining) * 1000 / refillRate`.
- `storage/BucketStateStore` — storage abstraction with a single `tryConsume(key, tokens, capacity, refillRate) → BucketCheckResult` method. This is the seam where Redis will plug in.
- `storage/impl/InMemoryBucketStateStore` — `ConcurrentHashMap<String, BucketState>` keyed by storage key. Each bucket is updated under `synchronized (bucket)`; lazy refill is computed from `System.nanoTime()` deltas and capped at `capacity`. New keys start at full capacity.

**Models** (all under `model/`): `RateLimiterAlgorithmRequest` (record: `key`, `tokensRequired`), `RateLimiterAlgorithmResponse` (record: `allowed`, `remainingToken`, `retryAfterMs`), `BucketCheckResult` (record: `allowed`, `remaining`), `BucketState` (mutable POJO holding `remainingToken` + `lastRefillTimestampInNanos` — guarded by `synchronized` on the instance in the in-memory store).

**Note on package naming:** the application code lives under `io.sriki.distributed_rate_limitter` (with the typo+underscore preserved across the codebase). The proto-generated code is under `io.sriki.ratelimiter.proto`. Search both when looking for symbols.

**Ports:**
- gRPC server: `9090` (HTTP/2 only — do not send HTTP/1.x requests here)
- Actuator/metrics: `8080` (HTTP/1.1; `application.yaml` exposes `health, prometheus, info, metrics, thread-dump`)

**Configuration** (`application.yaml`):
- `token.bucket.bucketCapacity` (default `100`) and `token.bucket.refillRatePerSecond` (default `10`) — bound to `TokenBucketConfigurationProperties`. Tests under `src/test/resources/application-test.yaml` may override these; activate with `@ActiveProfiles("test")`.

**Dependencies of note:**
- `spring-boot-starter-grpc-server` — Spring Boot 4.x native gRPC support
- `spring-boot-starter-actuator` + `micrometer-registry-prometheus` — metrics exposure
- `spring-boot-starter-grpc-test` — for gRPC integration tests (use this rather than spinning up netty manually)
