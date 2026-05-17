[![CI](https://github.com/Srikantha2001/distributed-rate-limitter/actions/workflows/ci.yml/badge.svg)](https://github.com/Srikantha2001/distributed-rate-limitter/actions/workflows/ci.yml)

# Distributed-Rate-Limiter

A gRPC-based distributed rate limiter built on Java 21 (virtual threads), Spring Boot, and Redis. 
Pluggable algorithms, observable, designed to fail gracefully.

> **Status: Early development.** Currently runs as a single-node skeleton with a hardcoded response. Token bucket + Redis-backed shared state coming in v0.5.

## Why this project

Rate limiting is one of the most universally-needed primitives in backend systems, and a small but rich slice of distributed systems engineering — touching atomicity, concurrency, network IPC, and graceful degradation. I'm building this both to deepen my distributed-systems intuition and to have a working artifact behind the concepts I work with day-to-day.

## Current state (v0.0.1)

- ✅ gRPC service skeleton (`CheckLimit` RPC)
- ✅ Spring Boot app boots, exposes `/actuator/health` and `/actuator/prometheus`
- ✅ CI runs `mvn verify` on every push
- ✅ Token bucket algorithm 
- ⏳ Redis-backed distributed state (next)
- ⏳ Sliding window algorithm
- ⏳ Graceful degradation when Redis is unreachable
- ⏳ Benchmarks

## Quick start

```bash
# Requires Java 21+ and Maven 3.9+
mvn spring-boot:run

# In another terminal — verify gRPC server is up
grpcurl -d '{"client_id":"id1", "resource":"r1", "tokens":3}' -plaintext localhost:9090 io.sriki.ratelimiter.RateLimiterService.CheckRateLimit
# → { "allowed": true, "remaining": "100", "reset_after_ms": "1000"}(currently hardcoded)

# Metrics
curl localhost:8080/actuator/prometheus | grep ratelimiter
```

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

See [docs/architecture.md](docs/architecture.md). Note: this is an evolving design — current implementation is a single-node stub.

## Roadmap

| Version | Scope                                                                             |
|---------|-----------------------------------------------------------------------------------|
| v0.0.1  | Runnable skeleton, hardcoded response                                             |
| v0.1    | Token bucket, in-memory, single-node (current)|                                    |
| v0.5    | Redis-backed, multi-node, basic observability                                     |
| v1.0    | Sliding window option, distributed coordination, benchmarks, graceful degradation |
