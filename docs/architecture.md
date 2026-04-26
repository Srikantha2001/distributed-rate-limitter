# Architecture (evolving)

> This document describes the *intended* architecture. The current implementation lags this design. See README for what's actually built.

## High-level
![distributedRateLimitter](distributedRateLimiter.excalidraw.png)
## Components (planned)

- **gRPC service layer** — accepts `CheckLimit` requests, routes to active algorithm
- **Algorithm strategy** — pluggable; v0.1 will ship token bucket, v1.0 adds sliding window
- **Storage layer** — Redis for shared state across instances. Atomic operations via Lua scripts.
- **Observability** — Micrometer → Prometheus. Histograms for latency, counters for allow/deny.

## Open questions (TBD by version)

- [ ] Fail-open vs fail-closed when Redis is unreachable? — *decision in v0.5*
- [ ] Consistent hashing vs centralised counters? — *decision in v1.0*
- [ ] How are rate-limit rules configured? Static YAML vs dynamic API? — *defer past v1.0*