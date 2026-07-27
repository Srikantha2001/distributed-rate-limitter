[![CI](https://github.com/Srikantha2001/distributed-rate-limitter/actions/workflows/ci.yml/badge.svg)](https://github.com/Srikantha2001/distributed-rate-limitter/actions/workflows/ci.yml)

# Distributed-Rate-Limiter

A gRPC-based distributed rate limiter built on Java 21 (virtual threads), Spring Boot, and Redis. 
Pluggable algorithms, observable, designed to fail gracefully.

> **Status: Early development.** Token bucket rate limiter with pluggable storage. In-memory mode works out of the box; Redis-backed distributed state is available behind a Spring profile.

## Why this project

Rate limiting is one of the most universally-needed primitives in backend systems, and a small but rich slice of distributed systems engineering — touching atomicity, concurrency, network IPC, and graceful degradation. I'm building this both to deepen my distributed-systems intuition and to have a working artifact behind the concepts I work with day-to-day.

## Current state (v0.1)

- ✅ gRPC service skeleton (`CheckLimit` RPC)
- ✅ Spring Boot app boots, exposes `/actuator/health` and `/actuator/prometheus`
- ✅ CI runs `mvn verify` on every push, plus Redis integration tests
- ✅ Token bucket algorithm (in-memory, single-node)
- ✅ Redis-backed distributed state (opt-in via `redis` profile)
- ⏳ Sliding window algorithm
- ⏳ Graceful degradation when Redis is unreachable
- ⏳ Benchmarks

## Quick start

```bash
# Requires Java 25+ and Maven 3.9+
mvn spring-boot:run

# In another terminal — verify gRPC server is up
grpcurl -d '{"client_id":"id1", "resource":"r1", "tokens":3}' -plaintext localhost:9090 io.sriki.ratelimiter.v1.RateLimiterService.CheckRateLimit
# → { "allowed": true, "remaining": "97" }

# Metrics
curl localhost:8080/actuator/prometheus | grep ratelimiter
```

## Running with Redis

```bash
# Start Redis (Docker Compose profile)
docker compose --profile redis up -d

# Run the application with the redis profile
mvn spring-boot:run -Dspring-boot.run.profiles=redis
```

In `redis` profile the application uses a Redis-backed token bucket implemented with a Lua script for atomic consume/refill operations. The Redis health indicator is active only in this profile.

## API

```protobuf
// Service for checking whether the request to be allowed or rate-limited
service RateLimiterService {
  // Checks atomically,whether requested tokens are available for given (client_id, resource) pair, and
  // if available consumes them and sets allowed to true. false otherwise
  //
  // Errors :
  // INVALID_ARGUMENT : client_id or resource not sticking to proper format.
  // UNAVAILABLE : service not reachable
  rpc CheckRateLimit(CheckRateLimitRequest) returns (CheckRateLimitResponse);
}

message CheckRateLimitRequest {
  // Required : Identity of the caller : ip / api_key. Must match alphanumeric only [A-Za-z0-9]+. INVALID_ARGUMENT if violated
  string client_id = 1;

  // Required:  endpoint/ operation requested , free form but avoid characters(specifically ':')
  string resource = 2;

  // Optional : number of tokens to be consumed representing the cost. default to 1 if not found or set to 0
  uint32 tokens = 3;
}

message CheckRateLimitResponse {
  // true if the request is allowed and tokens are consumed. false if request is reject and tokens are not consumed
  bool allowed = 1;
  // remaining tokens count after the request. even if the request is rejected, this returns the updated count
  uint64 remaining = 2;

  // represents time after which user need to retry to get success response in ms. 0 if it is already allowed
  uint64 retry_after_ms = 3;
}
```

## Architecture

See [docs/architecture.md](docs/architecture.md). Note: this is an evolving design — current implementation supports both single-node in-memory and Redis-backed modes.

## Testing

```bash
# Run the non-Docker test suite
mvn test

# Run Redis-backed integration tests (requires Docker)
mvn test -Dtest=RedisBucketStateStoreTest -Ddocker.enabled=true
```

## Roadmap

| Version | Scope                                                                             |
|---------|-----------------------------------------------------------------------------------|
| v0.0.1  | Runnable skeleton, hardcoded response                                             |
| v0.1    | Token bucket, in-memory + Redis-backed via profile (current)                      |
| v0.5    | Multi-node validation, basic observability                                        |
| v1.0    | Sliding window option, distributed coordination, benchmarks, graceful degradation |
