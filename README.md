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
- ⏳ Token bucket algorithm (next)
- ⏳ Redis-backed distributed state
- ⏳ Sliding window algorithm
- ⏳ Graceful degradation when Redis is unreachable
- ⏳ Benchmarks

## Quick start

```bash
# Requires Java 21+ and Maven 3.9+
mvn spring-boot:run

# In another terminal — verify gRPC server is up
grpcurl -d '{"client_id":"id1", "resource":"r1"}' -plaintext localhost:9090 io.sriki.ratelimiter.RateLimiterService.CheckRateLimit
# → { "allowed": true, "remaining": "100", "reset_after_ms": "1000"}(currently hardcoded)

# Metrics
curl localhost:8080/actuator/prometheus | grep ratelimiter
```

## API

```protobuf
service RateLimiterService {
  rpc CheckRateLimit(RateLimitRequest) returns (RateLimitResponse);
}

message RateLimitRequest {
  string client_id = 1;
  string resource   = 2;
}

message RateLimitResponse {
  bool   allowed        = 1;
  int64  remaining      = 2;
  int64  reset_after_ms = 3;
}

```

## Architecture

See [docs/architecture.md](docs/architecture.md). Note: this is an evolving design — current implementation is a single-node stub.

## Roadmap

| Version | Scope |
|---------|-------|
| v0.0.1  | Runnable skeleton, hardcoded response (current) |
| v0.1    | Token bucket, in-memory, single-node |
| v0.5    | Redis-backed, multi-node, basic observability |
| v1.0    | Sliding window option, distributed coordination, benchmarks, graceful degradation |
