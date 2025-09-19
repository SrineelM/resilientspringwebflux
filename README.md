Resilient Spring WebFlux Proof of Concept
AI Coding Agent Instructions

This project includes a .github/copilot-instructions.md file to guide AI coding agents (like GitHub Copilot) in understanding the architecture, workflows, conventions, and integration points specific to this codebase. This ensures automated coding agents generate code and suggestions that fit the project’s structure and standards, improving productivity and code quality. See .github/copilot-instructions.md for details.

Overview

This project demonstrates a scalable, secure, observable, and resilient reactive Java application built with Spring WebFlux. It integrates:
- Security hardening (JWT rotation, extended claims, selective CSRF, strict headers)
- Messaging reliability (Kafka + ActiveMQ with DLQ, correlation & tracing headers, transactional outbox)
- Resilience patterns (circuit breaker, retry, rate limiting, graceful shutdown)
- Observability (Micrometer, OpenTelemetry tracing, Prometheus, Zipkin)
- Modular profile-driven behavior (prod vs local/dev stubs)

Key Features

Security:
- JWT auth with rotating keys, refresh support, issuer/audience enforcement, extended claim validation (type/client_id/version).
- SecretProvider abstraction for external secret rotation.
- Selective CSRF toggle (disabled by default for pure API, can be enabled).
- HMAC-signed webhook endpoints with replay protection potential.
- Strict security headers: CSP, Referrer-Policy, Frame-Deny, Permissions-Policy.

Resilience:
- Resilience4j circuit breakers, retries, bulkheads & time limiters.
- Circuit breaker + exponential backoff around outbox publishing.
- Reactive sliding-window rate limiting: Redis in prod, in-memory for dev/test.

Messaging Reliability:
- Kafka & ActiveMQ producers with correlationId + W3C traceparent propagation.
- Dead-letter handling (Kafka DLQ via suffix; ActiveMQ DLQ reroute on processing failure).
- Transactional Outbox pattern (`message_outbox` table) with reactive dispatcher: NEW -> IN_PROGRESS -> PUBLISHED / FAILED, retry & circuit breaker.
- Profile isolation: local/dev use stub producers (no external brokers required).

Observability:
- Micrometer metrics, composite registries, Prometheus endpoint.
- OpenTelemetry tracing bridge with custom correlation & W3C traceparent support.
- Structured logging (logstash encoder) and correlation id propagation.

Micrometer Baggage (How-To)
---------------------------
This project demonstrates propagating custom context using W3C Baggage via Micrometer/OpenTelemetry.

- Configure baggage keys in `application.yml`:
    - `management.tracing.baggage.remote-fields`: correlationId, userId, tenantId
    - `management.tracing.baggage.correlation.fields`: correlationId, userId, tenantId (adds to MDC/logs)
- Send headers on incoming requests:
    - `X-Correlation-Id`, `X-User-Id`, `X-Tenant-Id`
- The `BaggageHeaderFilter` maps these headers into OTel Baggage so they propagate downstream, and also
    puts them into Reactor Context for easy access in reactive code.
- Read values in code via Reactor Context or Tracer baggage (example):
    - Reactor Context: `Mono.deferContextual(ctx -> Mono.just(ctx.getOrDefault("correlationId", "N/A")))`
    - Micrometer Tracer: `tracer.getBaggage("correlationId").get()` (if a span/scope exists)

Try it:
- Call any endpoint with headers and observe logs include `correlationId` and tracing shows baggage keys.
- Example headers: `X-Correlation-Id: demo-123`, `X-User-Id: alice`, `X-Tenant-Id: acme`.

Testing:
- Unit tests for outbox persistence, tracing header generation, JWT extended claims, rate limiting behavior.
- Placeholder (disabled) DLQ test scaffold awaiting configurable embedded Kafka listener.

Profiles & Environments:
- local | dev: H2 in‑memory, stub messaging, in-memory rate limiter.
- test: H2 + disabled dispatcher sends, in-memory limiter, configurable topics.
- prod: PostgreSQL (expected), real Kafka/ActiveMQ, Redis rate limiter, full dispatcher.

Cloud-Native:
- Graceful shutdown, boundedElastic offload for blocking I/O (JMS), container-ready image (Dockerfile), health & readiness endpoints.


Code Documentation & Educational Value
--------------------------------------
All Java source files in this project are thoroughly documented with:
- Class-level Javadocs explaining the purpose and context of each class or interface.
- Method-level Javadocs describing parameters, return values, and behavior.
- Inline comments clarifying key logic, design decisions, and best practices.

This makes the codebase highly accessible for beginners and new contributors, serving as a learning resource for:
- Spring WebFlux, reactive programming, and modern Java idioms
- Security, messaging, and observability patterns
- Clean/hexagonal architecture and testable design

If you are new to the project, you can browse any Java file to find clear explanations of its role and implementation details.

Prerequisites

JDK 17+

IntelliJ IDEA / Gradle / Git

Docker (for observability stack, Kafka, ActiveMQ, PostgreSQL in local/dev)

Optional: Postman or curl for API testing

Messaging Reliability Architecture

1. Producers (Kafka / ActiveMQ)
    - Inject correlationId and traceparent if absent.
    - On Kafka send failure, automatic DLQ publish to `<topic><dlq-suffix>`.

2. Consumers
    - Extract correlation + traceparent to Reactor Context for downstream processing.
    - ActiveMQ consumer sends failing messages (forced or exception) to configured DLQ destination with diagnostic headers.

3. Transactional Outbox
    - `OutboxPublisher.persistEvent(...)` writes NEW rows with JSON headers.
    - `OutboxDispatcher` (non-local/dev) batches NEW -> IN_PROGRESS atomically, publishes with retry + circuit breaker, updates status & published_at.
    - Supports dual-publish (Kafka + ActiveMQ) behind feature flags.

4. Tracing
    - `TracingHeaderUtil` ensures W3C `traceparent` header generation; reused across outbox, Kafka, ActiveMQ.

Security Enhancements

Implemented recommendations from security review:
- Key rotation & previous key validation (`JwtUtil#validateWithRotation`).
- Extended claim enforcement: token type=access, allowed client ids, min version.
- Selective CSRF enabling via `security.csrf.enabled` property.
- Enhanced rate limiting filter choosing user principal key over IP when authenticated.
- Repository-backed credentials replacing demo static values; password hashing via DelegatingPasswordEncoder.

Rate Limiting
- Prod: Redis sliding window LUA script (precise) with ZSET pruning.
- Dev/Test: InMemory limiter (low thresholds) enables deterministic 429 testing.

Running Locally
```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```
Stubs prevent external Kafka/ActiveMQ/Redis requirements; H2 auto-initializes schema.

Running Tests
```bash
./gradlew test
```
Tests cover JWT extended claims, outbox persistence, rate limiting (429), tracing headers.

Configuration Highlights (selected)

| Property | Purpose | Example |
|----------|---------|---------|
| security.jwt.secret / keys | JWT signing key(s) & rotation | `application-prod.yml` |
| security.csrf.enabled | Toggle selective CSRF | false |
| messaging.kafka.dlq-suffix | Kafka DLQ topic suffix | `-dlq` |
| messaging.kafka.consumer.topic | Consumer topic (configurable) | `demo-topic` |
| outbox.dispatch.enableKafka / enableActiveMq | Toggle outbox publishing targets | true/true |
| outbox.dispatch.batchSize | Batch size per poll cycle | 25 |
| outbox.dispatch.interval.ms | Poll interval | 5000 |
| webhook.rate-limit / window | Rate limit & window (prod Redis) | 30 / 60s |

Developer Tips
- Use `local` profile for fastest startup (no external brokers).
- Add new outbox event types via `persistEvent()` then rely on dispatcher.
- For DLQ testing, make consumer topic configurable (already property-driven) and consider enabling embedded Kafka test.

Planned / Optional Enhancements
- Embedded Kafka DLQ integration test (enable placeholder).
- Distinguish transient vs permanent outbox failures (no-retry classification).
- Adopt OpenTelemetry automatic instrumentation for JMS & Kafka.
- Persist structured header map (currently JSON) with schema evolution strategy.

Quick Start
Clone the repository
