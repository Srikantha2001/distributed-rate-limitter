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

A **Spring Boot gRPC server** that implements a distributed rate limiter. Currently a single-node skeleton; Redis-backed shared state and real algorithms are planned. See `docs/architecture.md` for the intended design before making structural changes.

**Layers:**

- `src/main/proto/rate_limiter.proto` — gRPC contract. Proto package `io.sriki.ratelimiter.v1`; generated Java lands in `io.sriki.ratelimiter.proto` (note: the proto package and the Java package are deliberately different).
- `grpc/RateLimiterServiceImpl` — `@GrpcService` extending the generated `RateLimiterServiceGrpc.RateLimiterServiceImplBase`. **Currently returns a hardcoded stub response (`allowed=true, remaining=100`) — this is intentional for v0.0.1, not a bug.** Real logic lands here as algorithms are added.
- `algorithm/RateLimiterAlgorithm` — strategy interface (`isAllowed(clientId, resource) → boolean`) for pluggable algorithms (token bucket, sliding window, …). Defined but not yet wired into the service impl.

**Note on package naming:** the application code lives under `io.sriki.distributed_rate_limitter` (with the typo+underscore preserved across the codebase). The proto-generated code is under `io.sriki.ratelimiter.proto`. Search both when looking for symbols.

**Ports:**
- gRPC server: `9090` (HTTP/2 only — do not send HTTP/1.x requests here)
- Actuator/metrics: `8080` (HTTP/1.1; `application.yaml` exposes `health, prometheus, info, metrics, thread-dump`)

**Dependencies of note:**
- `spring-boot-starter-grpc-server` — Spring Boot 4.x native gRPC support
- `spring-boot-starter-actuator` + `micrometer-registry-prometheus` — metrics exposure
- `spring-boot-starter-grpc-test` — for gRPC integration tests (use this rather than spinning up netty manually)
