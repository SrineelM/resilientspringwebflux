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

Backpressure Architecture
--------------------------

Every reactive pipeline in this project has an explicit backpressure strategy. The table below
documents where each strategy is applied and why.

| Layer | Component | Strategy | Rationale |
|-------|-----------|----------|-----------|
| HTTP SSE | `ReactiveStreamController.streamUsersSse()` | `onBackpressureDrop` (before `.map()`) | `Flux.interval` is a hot source. Ticks are dropped **before** object allocation so slow SSE clients don't waste CPU. |
| HTTP NDJSON | `ReactiveStreamController.streamUsersNdjson()` | Native Reactive Streams pull | `Flux.range` is a cold, cooperative source; no extra operator needed. |
| HTTP File | `ReactiveStreamController.streamFile()` | `DataBufferUtils.read` chunk pull | WebFlux response sink calls `request(1)` per chunk; `fileChunkBytes` (default 64 KB) controls memory per chunk. |
| Kafka SSE sim | `DemoKafkaController.consumeMessages()` | `onBackpressureLatest()` | Hot interval source; slow SSE client always gets the newest tick. `onErrorResume` (not `onErrorContinue`) used for safe error handling. |
| Kafka Consumer | `ReactiveKafkaConsumer` | `KafkaReceiver` pull + `flatMap(..., N)` | True Reactive Streams pull: Kafka poll loop respects `request(n)`. `concurrencyLimit` caps parallel processing. Offset acknowledged **after** processing. |
| Kafka Producer | `KafkaProducerConfig` → `KafkaSender` | `maxInFlight(256)` | Caps unacknowledged sends in flight; prevents memory growth under burst producer traffic. |
| ActiveMQ Consumer | `ReactiveActiveMqConsumer` | Bounded `Sinks.Many` buffer (1 000) | Prevents OOM when subscribers are slow. `EmitResult` checked; overflow messages routed to DLQ instead of silently dropped. |
| Outbox Dispatcher | `OutboxDispatcher.dispatchBatch()` | `flatMap(..., dispatchConcurrency)` | Configurable concurrency cap prevents broker/DB overload. `AtomicBoolean` guard prevents overlapping scheduler invocations. |
| Database | `UserService.findAll()` / `searchUsers()` | `.limitRate(100)` | Caps R2DBC row prefetch so large tables don't buffer entire result sets in memory. |

### Configuration Properties

```yaml
# Reactive Stream backpressure buffers
streaming.backpressure.buffer.size: 50      # items buffered before drop

# File streaming chunk size (64 KB default for good I/O throughput)
streaming.file.chunk.bytes: 65536

# Kafka consumer
messaging.kafka.consumer.concurrency: 4    # max parallel records processed
messaging.kafka.dlq-suffix: -dlq           # DLQ topic suffix

# Kafka producer
messaging.kafka.producer.max-in-flight: 256 # max unacked sends

# ActiveMQ consumer sink
activemq.consumer.sink.buffer.capacity: 1000 # max buffered JMS messages

# Outbox dispatcher
outbox.dispatch.concurrency: 4             # max parallel event dispatches
outbox.dispatch.batchSize: 25              # SQL LIMIT per poll cycle
```

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

## Quick Start Guide

### Prerequisites
- **JDK 17+** installed
- **Gradle 8.9+** (wrapper included)
- **8GB RAM minimum** for local development
- **Docker** (optional, for full stack)
- **Postman** (optional, for API testing)

### Clone and Build
```bash
git clone https://github.com/your-org/resilientspringwebflux.git
cd resilientspringwebflux

# Build the project
./gradlew clean build

# Run tests
./gradlew test
```

### Running Locally (Development Mode)

**Simplest way** - no external dependencies needed:

```bash
# Start application with dev profile (H2 in-memory, messaging stubs)
./gradlew bootRun --args='--spring.profiles.active=dev'
```

Application starts at: **http://localhost:8080**
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI Docs**: http://localhost:8080/v3/api-docs

**Verify it's running**:
```bash
curl http://localhost:8080/actuator/health
```

### Testing the Application

#### 1. Get a JWT Token
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"password"}'
```

Save the `token` from the response.

#### 2. Create a User
```bash
curl -X POST http://localhost:8080/api/users \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "johndoe",
    "email": "john.doe@example.com",
    "fullName": "John Doe"
  }'
```

#### 3. Get All Users
```bash
curl -X GET http://localhost:8080/api/users \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

#### 4. Access Metrics
```bash
# Prometheus metrics
curl http://localhost:8080/actuator/prometheus

# Health check with details
curl http://localhost:8080/actuator/health
```

### Using Postman

1. **Import Collection**: See `postman.md` for complete API documentation
2. **Set Variables**:
   - `base_url`: http://localhost:8080
   - `jwt_token`: (obtained from login)
3. **Run Tests**: Follow scenarios in `postman.md`

Detailed Postman setup and test data available in:
- **postman.md** - API endpoints and sample requests
- **testdata.md** - Complete test data reference

### Testing with Docker Compose (Full Stack)

```bash
# Start PostgreSQL, Kafka, Zipkin, Redis
docker-compose up -d

# Set environment variables
export SPRING_PROFILES_ACTIVE=prod
export JWT_SECRET="your-secret-key-min-256-bits-aabbccddeeff00112233445566778899"
export PROD_DB_HOST=localhost
export PROD_DB_NAME=resilient_db
export PROD_DB_USERNAME=postgres
export PROD_DB_PASSWORD=postgres

# Run application
./gradlew bootRun
```

### H2 Console (Dev Profile Only)

Access the H2 database console at: **http://localhost:8080/h2-console**

- **JDBC URL**: `jdbc:h2:mem:testdb`
- **Username**: `sa`
- **Password**: (leave empty)

## Documentation

### For Developers
- **instructions.md** - Comprehensive developer guide with architecture, patterns, and how-to guides
- **review.md** - Architectural review and best practices analysis
- **testdata.md** - Complete test data for all scenarios
- **postman.md** - Postman collection and API testing guide

### Key Configuration Files
- **application.yml** - Base configuration (shared)
- **application-dev.yml** - Development (local, 8GB RAM optimized)
- **application-prod.yml** - Production (PostgreSQL, Kafka, Redis)
- **application-test.yml** - Test configuration

---

## Architecture Overview

This project demonstrates **production-grade Spring WebFlux** patterns:


### Design Patterns
- ✅ **Hexagonal Architecture** (Ports & Adapters)
- ✅ **Transactional Outbox Pattern** (reliable messaging)
- ✅ **Circuit Breaker, Retry, Bulkhead** (Resilience4j)
- ✅ **Reactive Programming** (Project Reactor)

### ASCII Architecture Diagram
```
      +-----------------------------+
      |        External Clients      |
      +-----------------------------+
           |
           v
      +-----------------------------+
      |      WebFlux Controllers     |
      +-----------------------------+
           |
           v
   +------------------- Security & Filters -------------------+
   |  JWT Auth  |  Rate Limiting  |  Baggage/Tracing Filters  |
   +---------------------------------------------------------+
           |
           v
      +-----------------------------+
      |      Service Layer           |
      |  (Business Logic, Ports)     |
      +-----------------------------+
           |
           v
   +------------------- Adapters -------------------+
   |  Repository (R2DBC)  |  Messaging (Kafka/AMQ)  |
   |  Outbox Dispatcher   |  Notification/Audit     |
   +------------------------------------------------+
           |
           v
      +-----------------------------+
      |      External Systems        |
      |  DB / Kafka / ActiveMQ /     |
      |  Redis / Zipkin / Prometheus |
      +-----------------------------+

  [Observability: Tracing, Metrics, Logging flows through all layers]
  [Resilience: Circuit Breakers, Retries, Bulkheads in Service/Adapter]
  [Messaging Reliability: Transactional Outbox, DLQ, Correlation]
```

### Technology Stack
| Component | Technology | Purpose |
|-----------|------------|---------|
| Framework | Spring Boot 3.3.5 | Main framework |
| Reactive | Spring WebFlux 6.x | Reactive web |
| Security | Spring Security + JWT | Authentication |
| Database | R2DBC (H2/PostgreSQL) | Reactive DB |
| Messaging | Kafka + ActiveMQ | Event streaming |
| Observability | Micrometer + OpenTelemetry | Tracing & metrics |
| Fault Tolerance | Resilience4j 2.1.0 | Circuit breakers |

### Project Structure
```
src/main/java/com/resilient/
├── adapters/        # Hexagonal architecture adapters
├── config/          # Spring configuration (Security, DB, Messaging)
├── controller/      # REST API controllers
├── dto/             # Data Transfer Objects
├── exception/       # Global exception handling
├── filter/          # WebFlux filters (tracing, baggage, correlation)
├── messaging/       # Kafka, ActiveMQ, Outbox pattern
├── model/           # Domain models
├── observability/   # Custom metrics and tracing
├── ports/           # Port interfaces (hexagonal architecture)
├── repository/      # R2DBC repositories
├── security/        # JWT, authentication, rate limiting
└── service/         # Business logic services
```

---

## Key Features Demonstrated

### 1. Security
- ✅ JWT authentication with key rotation
- ✅ Token blacklisting for logout
- ✅ Rate limiting (Redis + in-memory)
- ✅ HMAC webhook signature validation
- ✅ Extended JWT claims validation

### 2. Observability
- ✅ Distributed tracing (OpenTelemetry + Zipkin)
- ✅ W3C Trace Context propagation
- ✅ Custom baggage fields (correlationId, userId, tenantId)
- ✅ Prometheus metrics export
- ✅ Structured JSON logging with MDC

### 3. Fault Tolerance
- ✅ Circuit breakers per service
- ✅ Retry with exponential backoff
- ✅ Bulkhead for concurrency control
- ✅ Fallback methods for degraded operations
- ✅ Graceful shutdown

### 4. Messaging Reliability
- ✅ Transactional Outbox pattern
- ✅ Dead Letter Queue (DLQ) handling
- ✅ Correlation ID propagation
- ✅ At-least-once delivery guarantee
- ✅ Kafka & ActiveMQ integration

### 5. Reactive Programming
- ✅ Non-blocking I/O throughout
- ✅ Backpressure handling (e.g., AuditService event streaming)
- ✅ Proper Mono/Flux usage
- ✅ Custom schedulers for blocking operations
- ✅ Context propagation

---

## Testing

### Run All Tests
```bash
./gradlew test
```

### Run Tests with Coverage
```bash
./gradlew test jacocoTestReport
# Report: build/reports/jacoco/test/html/index.html
```

### Test Profiles
- **dev**: H2 in-memory, messaging stubs, minimal config
- **test**: H2 in-memory, disabled schedulers, fast tests
- **prod**: PostgreSQL, Kafka, ActiveMQ, full features

### Testing Tools Used
- **JUnit 5** - Test framework
- **StepVerifier** - Reactive testing
- **MockBean** - Mocking dependencies
- **WebTestClient** - Integration testing
- **Testcontainers** - Database containers (optional)

---

## Configuration Profiles

### Development (dev)
- H2 in-memory database
- Messaging stubs (no Kafka/ActiveMQ needed)
- In-memory rate limiter
- Debug logging enabled
- Tracing disabled (performance)
- **Optimized for 8GB RAM**

### Production (prod)
- PostgreSQL database with connection pooling
- Real Kafka + ActiveMQ producers/consumers
- Redis-based rate limiter
- Full distributed tracing (Zipkin)
- Info-level logging
- Circuit breakers tuned for production

### Test (test)
- H2 in-memory database
- Disabled outbox dispatcher
- Configurable test topics
- Fast startup, minimal resources

---

## Monitoring & Health Checks

### Actuator Endpoints

| Endpoint | Purpose |
|----------|---------|
| `/actuator/health` | Health status |
| `/actuator/health/readiness` | Readiness probe (K8s) |
| `/actuator/health/liveness` | Liveness probe (K8s) |
| `/actuator/prometheus` | Prometheus metrics |
| `/actuator/metrics` | Available metrics |
| `/actuator/info` | Application info |
| `/swagger-ui.html` | Swagger UI Documentation |
| `/v3/api-docs` | OpenAPI 3 Specification |

### Custom Health Indicators
- Database connectivity (R2DBC)
- Disk space
- Custom business health checks

### Metrics Available
- HTTP request duration
- Circuit breaker state
- Retry attempts
- Rate limiting counters
- Custom business metrics

---

## Troubleshooting

### Port Already in Use
```bash
# Find process on port 8080
lsof -i :8080

# Kill it
kill -9 <PID>
```

### JWT Token Issues
1. Check token expiration
2. Verify issuer matches config
3. Ensure audience is correct
4. Enable debug logging:
```bash
export LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_SECURITY=DEBUG
```

### Database Connection Issues
- Verify H2 console at `/h2-console` (dev profile)
- Check `application-dev.yml` for R2DBC URL
- Ensure schema.sql is executed

### Outbox Not Publishing
1. Check `outbox.dispatch.enabled=true`
2. Verify events in `message_outbox` table
3. Check Kafka/ActiveMQ connectivity (prod)
4. Enable debug logging for `com.resilient.messaging`

---

## Contributing

### Code Style
- Code formatting: `./gradlew spotlessApply`
- Auto-formatted before compilation
- Follows Palantir Java Format

### Git Workflow
1. Create feature branch
2. Make changes
3. Run tests: `./gradlew test`
4. Format code: `./gradlew spotlessApply`
5. Commit with conventional message
6. Create pull request

### Commit Message Format
Follow [Conventional Commits](https://www.conventionalcommits.org/):
```
feat: add new feature
fix: resolve bug
docs: update documentation
test: add tests
refactor: improve code structure
```

---

## Learning Resources

### Documentation Files
- **instructions.md** - Complete developer guide
- **review.md** - Architectural analysis and best practices
- **testdata.md** - Test data and examples
- **postman.md** - API testing guide

### External Resources
- [Spring WebFlux Docs](https://docs.spring.io/spring-framework/reference/web/webflux.html)
- [Project Reactor](https://projectreactor.io/docs/core/release/reference/)
- [Resilience4j Guide](https://resilience4j.readme.io/)
- [Spring Security WebFlux](https://docs.spring.io/spring-security/reference/reactive/index.html)

---

## License

This project is provided as a reference implementation for educational purposes.

---

## Support

- **Issues**: Create GitHub issue
- **Questions**: See `instructions.md` for detailed guides
- **Testing**: See `testdata.md` and `postman.md`

---

Clone the repository
