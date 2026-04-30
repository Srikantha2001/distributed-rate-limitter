# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Build and run all tests
mvn -B verify

# Run tests only
mvn test

# Build without tests
mvn -B package -DskipTests

# Run the application
mvn spring-boot:run
```

Proto sources are generated automatically during the build by `protobuf-maven-plugin` into `target/generated-sources/`.

## Architecture

This is a **Spring Boot gRPC server** (Java 25) that implements a distributed rate limiter.

**Key layers:**

- `src/main/proto/rate_limiter.proto` — defines the gRPC service contract. The Maven build generates Java stubs from this into `io.sriki.ratelimiter.proto`.
- `grpc/RateLimiterServiceImpl` — `@GrpcService` bean that extends the generated `RateLimiterServiceGrpc.RateLimiterServiceImplBase` and handles `CheckRateLimit` calls. This is where rate-limiting logic will live (currently returns a stub response).
- `algorithm/RateLimiterAlgorithm` — interface (`isAllowed(clientId, resource) → boolean`) intended to abstract the rate-limiting algorithm (token bucket, sliding window, etc.). Not yet wired into the service impl.

**Ports:**
- gRPC server: `9090` (HTTP/2 only — do not send HTTP/1.x requests here)
- Actuator/metrics : `8080` (HTTP/1.1) 

**Dependencies of note:**
- `spring-boot-starter-grpc-server` — Spring Boot 4.x native gRPC support
- `spring-boot-starter-actuator` + `micrometer-registry-prometheus` — metrics exposure

