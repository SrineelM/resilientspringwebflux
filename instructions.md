---
# Developer & Architect Guide

**Project:** Resilient Spring WebFlux POC
**Version:** 1.0.0
**Audience:** Senior Architects, Software Engineers, Tech Leads
**Last Updated:** November 1, 2025
---

## Table of Contents
1. [Project Overview](#1-project-overview)
2. [Architecture and Design Patterns](#2-architecture-and-design-patterns)
3. [Prerequisites and Setup](#3-prerequisites-and-setup)
4. [Running the Application](#4-running-the-application)
5. [Testing Guide](#5-testing-guide)
6. [Key Features and How to Use Them](#6-key-features-and-how-to-use-them)
7. [Adding New Features](#7-adding-new-features)
8. [Best Practices Demonstrated](#8-best-practices-demonstrated)
9. [Troubleshooting](#9-troubleshooting)
10. [References and Further Learning](#10-references-and-further-learning)
11. [Contributing](#11-contributing)
12. [Support and Community](#12-support-and-community)
---

## 1. Project Overview

### 1.1 Purpose
This project serves as a **comprehensive reference implementation** for building production-grade reactive applications using Spring WebFlux. It demonstrates industry best practices for:

- **Security**: JWT authentication, rate limiting, HMAC signature validation
- **Observability**: Distributed tracing, metrics, structured logging
- **Fault Tolerance**: Circuit breakers, retries, bulkheads, timeouts
- **Messaging**: Transactional outbox, Kafka, ActiveMQ with DLQ handling
- **Reactive Programming**: Proper Mono/Flux usage, backpressure, schedulers

### 1.2 Technology Stack (2023-2025)

| Component | Technology | Version |
|-----------|------------|---------|
| Language | Java | 17 LTS |
| Framework | Spring Boot | 3.3.5 |
| Reactive | Spring WebFlux | 6.x |
| Database | R2DBC (H2/PostgreSQL) | Latest |
| Security | Spring Security + JWT | 6.x |
| Messaging | Kafka + ActiveMQ | Latest |
| Observability | Micrometer + OpenTelemetry | Latest |
| Fault Tolerance | Resilience4j | 2.1.0 |
| Build Tool | Gradle | 8.9 |

### 1.3 Project Structure
```
src/
├── main/
│   ├── java/com/resilient/
│   │   ├── adapters/        # Hexagonal architecture adapters
│   │   ├── config/          # Spring configuration classes
│   │   ├── controller/      # REST API controllers
│   │   ├── dto/             # Data Transfer Objects
│   │   ├── exception/       # Exception handling
│   │   ├── filter/          # WebFlux filters (tracing, correlation)
│   │   ├── health/          # Custom health indicators
│   │   ├── messaging/       # Kafka, ActiveMQ, Outbox pattern
│   │   ├── model/           # Domain models
│   │   ├── observability/   # Tracing, metrics
│   │   ├── ports/           # Hexagonal architecture ports (interfaces)
│   │   ├── repository/      # R2DBC repositories
│   │   ├── security/        # JWT, auth, rate limiting
│   │   └── service/         # Business logic services
│   └── resources/
│       ├── application.yml  # Base configuration
│       ├── application-dev.yml   # Dev profile (local)
│       ├── application-prod.yml  # Production profile
│       └── schema.sql       # Database schema
└── test/                    # Unit and integration tests
```

---

---
## 2. Architecture and Design Patterns

### 2.1 Hexagonal Architecture (Ports and Adapters)

This project follows **Hexagonal Architecture** principles:

```
┌─────────────────────────────────────────┐
│          Controllers (Primary)          │
│   (HTTP, Messaging - Entry Points)      │
└──────────────┬──────────────────────────┘
               │
┌──────────────▼──────────────────────────┐
│          Business Logic Layer           │
│    (Services, Domain Models)            │
│    Uses: Ports (Interfaces)             │
└──────────────┬──────────────────────────┘
               │
┌──────────────▼──────────────────────────┐
│    Adapters (Secondary - Driven)        │
│  (Database, External APIs, Messaging)   │
│  Implements: Ports                      │
└─────────────────────────────────────────┘
```

**Example**:
- **Port**: `UserNotificationPort` (interface in `ports/`)
- **Adapter**: `NotificationAdapter` (implementation in `adapters/`)
- **Service**: `UserService` depends on `UserNotificationPort`, not the concrete adapter

### 2.2 Reactive Programming Pattern

All components use **Reactor** (Project Reactor) for non-blocking, asynchronous processing:

- **Mono**: Single value (0 or 1)
- **Flux**: Multiple values (0 to N)
- **Schedulers**: For offloading blocking operations
- **Backpressure**: Automatic flow control and explicit handling (e.g., `onBackpressureBuffer` in `AuditService.auditEventStream`)

### 2.3 Transactional Outbox Pattern

Guarantees **at-least-once** message delivery:

1. Business transaction saves entity + outbox event to DB atomically
2. Background dispatcher polls outbox table
3. Publishes events to Kafka/ActiveMQ
4. Updates status to PUBLISHED on success

### 2.4 Security Architecture

```
Request → RateLimitingWebFilter
       → BaggageHeaderFilter (correlationId)
       → AuthenticationWebFilter (JWT validation)
       → SecurityWebFilterChain (authorization)
       → Controller
```

---

---
## 3. Prerequisites and Setup

### 3.1 Required Software

| Software | Version | Purpose |
|----------|---------|---------|
| JDK | 17+ | Java runtime |
| Gradle | 8.9+ (wrapper included) | Build tool |
| Docker | 20+ | For PostgreSQL, Kafka, Zipkin (optional) |
| Git | 2.x | Version control |
| Postman | Latest | API testing (optional) |

### 3.2 IDE Setup (IntelliJ IDEA Recommended)

1. **Install Plugins**:
   - Lombok
   - Spring Boot
   - Database Navigator (optional)

2. **Enable Annotation Processing**:
   ```
   Settings → Build → Compiler → Annotation Processors
   ☑ Enable annotation processing
   ```

3. **Code Style**:
   - The project uses Spotless for auto-formatting
   - Formatting is applied automatically before compilation

### 3.3 Clone and Build

```bash
# Clone the repository
git clone https://github.com/your-org/resilientspringwebflux.git
cd resilientspringwebflux

# Build the project
./gradlew clean build

# Run tests
./gradlew test

# Generate code coverage report
./gradlew jacocoTestReport
```

---

---
## 4. Running the Application

### 4.1 Quick Start (Development Mode)

**Simplest way** - requires no external dependencies:

```bash
# Run with dev profile (H2 in-memory, stubs for messaging)
./gradlew bootRun --args='--spring.profiles.active=dev'

# Or with environment variables
export SPRING_PROFILES_ACTIVE=dev
./gradlew bootRun
```

**Application will start on**: `http://localhost:8080`

### 4.2 With Docker Compose (Full Stack)

For testing with real Kafka, PostgreSQL, Zipkin:

```bash
# Start infrastructure
docker-compose up -d

# Run application with prod profile
export SPRING_PROFILES_ACTIVE=prod
export JWT_SECRET="your-secret-key-min-256-bits"
export PROD_DB_HOST=localhost
export PROD_DB_NAME=resilient_db
export PROD_DB_USERNAME=postgres
export PROD_DB_PASSWORD=postgres

./gradlew bootRun
```

### 4.3 Profile Selection

| Profile | Use Case | Database | Messaging | Tracing |
|---------|----------|----------|-----------|---------|
| **dev** | Local development | H2 (in-memory) | Stubs | Disabled |
| **test** | Unit/integration tests | H2 (in-memory) | Stubs | Disabled |
| **prod** | Production deployment | PostgreSQL | Kafka + ActiveMQ | Enabled (Zipkin) |

### 4.4 Verifying the Application

```bash
# Health check
curl http://localhost:8080/actuator/health

# Metrics
curl http://localhost:8080/actuator/metrics

# Prometheus metrics
curl http://localhost:8080/actuator/prometheus
```

---

---
## 5. Testing Guide

### 5.1 Running Tests

```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests UserServiceTest

# Run with coverage
./gradlew test jacocoTestReport
# Report: build/reports/jacoco/test/html/index.html
```

### 5.2 Unit Testing with StepVerifier

Example from the codebase:

```java
@Test
void testCreateUser() {
    UserRequest request = new UserRequest("john", "john@example.com", "John Doe");

    // Mock dependencies
    when(userRepository.existsByUsername("john")).thenReturn(Mono.just(false));
    when(userRepository.existsByEmail("john@example.com")).thenReturn(Mono.just(false));
    when(userRepository.save(any())).thenReturn(Mono.just(user));

    // Test with StepVerifier
    StepVerifier.create(userService.createUser(request))
        .assertNext(response -> {
            assertThat(response.username()).isEqualTo("john");
            assertThat(response.email()).isEqualTo("john@example.com");
        })
        .verifyComplete();
}
```

### 5.3 Integration Testing

Use `@SpringBootTest` with `WebTestClient`:

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class UserManagementIntegrationTest {

    @Autowired
    private WebTestClient webClient;

    @Test
    void shouldCreateUser() {
        webClient.post()
            .uri("/api/users")
            .header("Authorization", "Bearer " + jwtToken)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(new UserRequest("test", "test@example.com", "Test User"))
            .exchange()
            .expectStatus().isOk()
            .expectBody(UserResponse.class)
            .value(response -> {
                assertThat(response.username()).isEqualTo("test");
            });
    }
}
```

---

---
## 6. Key Features and How to Use Them

### 6.1 JWT Authentication

#### Obtaining a Token
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"password"}'
```

#### Using the Token
```bash
curl -X GET http://localhost:8080/api/users \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

#### Refreshing a Token
```bash
curl -X POST http://localhost:8080/api/auth/refresh \
  -H "Authorization: Bearer YOUR_EXISTING_TOKEN"
```

#### Logout (Token Blacklisting)
```bash
curl -X POST http://localhost:8080/api/auth/logout \
  -H "Authorization: Bearer YOUR_TOKEN"
```

### 6.2 Distributed Tracing

Send requests with correlation headers:

```bash
curl -X GET http://localhost:8080/api/users \
  -H "Authorization: Bearer TOKEN" \
  -H "X-Correlation-Id: 550e8400-e29b-41d4-a716-446655440000" \
  -H "X-User-Id: johndoe" \
  -H "X-Tenant-Id: acme-corp"
```

**Correlation ID will propagate**:
- Through all log statements (MDC)
- To outbox events
- To Kafka/ActiveMQ messages
- To Zipkin traces

### 6.3 Circuit Breaker (Resilience4j)

Configured in `application.yml`:

```yaml
resilience4j:
  circuitbreaker:
    configs:
      default:
        sliding-window-size: 10
        minimum-number-of-calls: 5
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
```

**Testing Circuit Breaker**:
1. Trigger multiple failures (e.g., invalid user IDs)
2. Circuit opens after 50% failure rate
3. Fallback method is called
4. After 30s, circuit goes to half-open
5. Successful calls close the circuit

### 6.4 Rate Limiting

**Default limits** (see `application.yml` for webhook endpoint):
- 30 requests per 60 seconds (prod with Redis)
- 10 requests per 60 seconds (dev/test in-memory)

**Testing**:
```bash
# Send rapid requests
for i in {1..15}; do
  curl -X POST http://localhost:8080/api/webhook/receive \
    -H "Authorization: Bearer TOKEN" \
    -H "X-Webhook-Secret: devwebhooksecret" \
    -d '{"event":"test"}'
done
# After threshold, expect 429 Too Many Requests
```

### 6.5 Transactional Outbox

**How it works**:

1. Service calls `OutboxPublisher.persistEvent()`:
```java
outboxPublisher.persistEvent(
    "User",           // aggregateType
    userId.toString(), // aggregateId
    "USER_CREATED",   // eventType
    userPayload,      // payload (JSON)
    headers           // metadata headers
).subscribe();
```

2. Event saved to `message_outbox` table (status=NEW)
3. `OutboxDispatcher` polls for NEW events
4. Publishes to Kafka/ActiveMQ
5. Updates status to PUBLISHED

**Database schema**:
```sql
CREATE TABLE message_outbox (
    id BIGSERIAL PRIMARY KEY,
    aggregate_type VARCHAR(255),
    aggregate_id VARCHAR(255),
    event_type VARCHAR(255),
    payload TEXT,
    headers TEXT,
    status VARCHAR(50),
    created_at TIMESTAMP,
    published_at TIMESTAMP
);
```

### 6.6 Custom Metrics

Add your own metrics:

```java
@Component
public class MyService {
    private final Counter myCounter;

    public MyService(MeterRegistry registry) {
        this.myCounter = Counter.builder("my.business.metric")
            .tag("type", "important")
            .register(registry);
    }

    public Mono<Void> doSomething() {
        myCounter.increment();
        // ... business logic
    }
}
```

### 6.7 Backpressure and Stream Auditing

The `AuditService` demonstrates explicit backpressure handling for event streams:

```java
public Flux<AuditResult> auditEventStream(Flux<AuditEvent> events) {
    return events
            // Explicit backpressure: buffer up to maxBatchSize items.
            // Drops oldest events if the buffer is full to prevent memory exhaustion.
            .onBackpressureBuffer(maxBatchSize, BufferOverflowStrategy.DROP_OLDEST)
            .flatMap(event -> auditUserAction(...), batchConcurrency);
}
```

This ensures the system remains stable even when receiving events faster than it can process them.

---

---
## 7. Adding New Features

### 7.1 Adding a New Secured Endpoint

**Step 1**: Create Controller

```java
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @GetMapping("/{id}")
    public Mono<OrderResponse> getOrder(@PathVariable Long id) {
        // Implementation
    }
}
```

**Step 2**: Update Security Configuration

Edit `WebFluxSecurityConfig.java`:

```java
.authorizeExchange(exchanges -> exchanges
    // ... existing rules
    .pathMatchers("/api/orders/**").authenticated()
    .anyExchange().authenticated()
)
```

**Step 3**: Add Service Layer

```java
@Service
public class OrderService {

    @CircuitBreaker(name = "orderService", fallbackMethod = "fallbackGetOrder")
    @Retry(name = "orderService")
    public Mono<Order> getOrder(Long id) {
        // Implementation
    }

    private Mono<Order> fallbackGetOrder(Long id, Throwable t) {
        log.warn("Fallback: returning cached order");
        return Mono.just(Order.cached());
    }
}
```

**Step 4**: Configure Resilience4j

Add to `application.yml`:

```yaml
resilience4j:
  circuitbreaker:
    instances:
      orderService:
        base-config: default
  retry:
    instances:
      orderService:
        max-attempts: 3
        wait-duration: 1s
```

### 7.2 Adding a New Outbox Event Type

**Step 1**: Publish event in service

```java
@Service
public class OrderService {
    private final OutboxPublisher outboxPublisher;

    public Mono<Order> createOrder(OrderRequest request) {
        return orderRepository.save(order)
            .flatMap(savedOrder -> {
                Map<String, String> headers = Map.of(
                    "correlationId", getCorrelationId(),
                    "eventVersion", "1.0"
                );

                return outboxPublisher.persistEvent(
                    "Order",
                    savedOrder.getId().toString(),
                    "ORDER_CREATED",
                    toJson(savedOrder),
                    headers
                ).thenReturn(savedOrder);
            });
    }
}
```

**Step 2**: OutboxDispatcher handles automatically

No code changes needed! The dispatcher will:
1. Poll the event
2. Publish to configured channels (Kafka/ActiveMQ)
3. Update status

### 7.3 Adding a New Custom Health Indicator

```java
@Component
public class ExternalServiceHealthIndicator implements ReactiveHealthIndicator {

    private final WebClient webClient;

    @Override
    public Mono<Health> health() {
        return webClient.get()
            .uri("/health")
            .retrieve()
            .toBodilessEntity()
            .map(response -> Health.up()
                .withDetail("status", "reachable")
                .build())
            .onErrorResume(ex -> Mono.just(Health.down()
                .withDetail("error", ex.getMessage())
                .build()))
            .timeout(Duration.ofSeconds(5))
            .onErrorResume(TimeoutException.class, ex ->
                Mono.just(Health.down()
                    .withDetail("error", "timeout")
                    .build()));
    }
}
```

---

---
## 8. Best Practices Demonstrated

### 8.1 Reactive Programming

✅ **DO**:
```java
// Use flatMap for dependent operations
userRepository.findById(id)
    .flatMap(user -> notificationService.notify(user))
    .map(UserResponse::from);

// Use defer for lazy evaluation
Mono.defer(() -> {
    String correlationId = getCurrentCorrelationId();
    return doSomething(correlationId);
});

// Proper error handling
service.doSomething()
    .onErrorResume(BusinessException.class, ex ->
        Mono.just(fallbackValue));
```

❌ **DON'T**:
```java
// Don't block in reactive chains
Mono<User> user = userRepository.findById(id)
    .map(u -> {
        String result = blockingCall(); // ❌ NEVER DO THIS
        return u;
    });

// Don't use .subscribe() in the middle of a chain
Mono<User> user = userRepository.findById(id);
user.subscribe(); // ❌ Breaks reactive chain
return user.map(...);
```

### 8.2 Security

✅ **DO**:
```java
// Use environment variables for secrets
@Value("${JWT_SECRET}")
private String jwtSecret;

// Validate all inputs
public Mono<User> createUser(@Valid UserRequest request) { }

// Use proper HTTP status codes
throw new UserNotFoundException("User not found"); // Returns 404
```

❌ **DON'T**:
```java
// Don't hardcode secrets
private String secret = "mysecret"; // ❌

// Don't expose internal errors
catch (Exception e) {
    return ResponseEntity.status(500)
        .body(e.getStackTrace()); // ❌ Security risk
}
```

### 8.3 Error Handling

✅ **DO**:
```java
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(UserNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse("NOT_FOUND", ex.getMessage()));
    }
}
```

### 8.4 Testing

✅ **DO**:
```java
// Use StepVerifier for reactive testing
StepVerifier.create(service.getUser(1L))
    .expectNextMatches(user -> user.id().equals(1L))
    .verifyComplete();

// Mock dependencies properly
@MockBean
private UserRepository userRepository;

when(userRepository.findById(1L))
    .thenReturn(Mono.just(testUser));
```

---

---
## 9. Troubleshooting

### 9.1 Common Issues

#### Issue: Application won't start - "Port 8080 already in use"

**Solution**:
```bash
# Find process using port 8080
lsof -i :8080

# Kill the process
kill -9 <PID>

# Or change port
export SERVER_PORT=8081
./gradlew bootRun
```

#### Issue: JWT validation fails

**Checklist**:
1. Token not expired? Check `exp` claim
2. Correct issuer? Check `iss` claim matches config
3. Correct audience? Check `aud` claim
4. Signature valid? Check JWT secret matches

**Debug**:
```bash
# Enable debug logging
export LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_SECURITY=DEBUG
./gradlew bootRun
```

#### Issue: Circuit breaker not working

**Verify**:
1. Check `@CircuitBreaker` annotation on service method
2. Verify resilience4j config in `application.yml`
3. Check metrics: `curl http://localhost:8080/actuator/metrics/resilience4j.circuitbreaker.calls`

#### Issue: OutboxDispatcher not publishing events

**Debug**:
1. Check `outbox.dispatch.enabled=true` in config
2. Verify events are in `message_outbox` table with status=NEW
3. Check logs for dispatcher errors
4. Ensure Kafka/ActiveMQ is running (in prod profile)

### 9.2 Debugging Tips

**Enable detailed logging**:
```yaml
logging:
  level:
    com.resilient: DEBUG
    org.springframework.security: DEBUG
    io.github.resilience4j: DEBUG
    reactor.netty: DEBUG
```

**H2 Console** (dev profile):
- URL: `http://localhost:8080/h2-console`
- JDBC URL: `jdbc:h2:mem:testdb`
- Username: `sa`
- Password: (empty)

**Reactor debugging**:
```java
// Enable in main class for development
Hooks.onOperatorDebug();
```

---

---
## 10. References and Further Learning

### 10.1 Official Documentation

- [Spring WebFlux Reference](https://docs.spring.io/spring-framework/reference/web/webflux.html)
- [Project Reactor Documentation](https://projectreactor.io/docs/core/release/reference/)
- [Spring Security for WebFlux](https://docs.spring.io/spring-security/reference/reactive/index.html)
- [Resilience4j User Guide](https://resilience4j.readme.io/)
- [R2DBC Documentation](https://r2dbc.io/)

### 10.2 Design Patterns

- [Hexagonal Architecture](https://alistair.cockburn.us/hexagonal-architecture/)
- [Transactional Outbox Pattern](https://microservices.io/patterns/data/transactional-outbox.html)
- [Circuit Breaker Pattern](https://martinfowler.com/bliki/CircuitBreaker.html)
- [Saga Pattern](https://microservices.io/patterns/data/saga.html)

### 10.3 Books

- "Reactive Spring" by Josh Long
- "Spring Security in Action" by Laurentiu Spilca
- "Release It!" by Michael T. Nygard (resilience patterns)
- "Building Microservices" by Sam Newman

### 10.4 Online Courses

- Spring Academy: Spring Boot and WebFlux courses
- Baeldung: Spring WebFlux tutorials
- Udemy: Reactive Programming with Spring WebFlux

---

---
## 11. Contributing

### 11.1 Code Style

This project uses **Spotless** for code formatting. Code is automatically formatted before compilation.

Manual formatting:
```bash
./gradlew spotlessApply
```

### 11.2 Git Workflow

1. Create feature branch: `git checkout -b feature/my-feature`
2. Make changes
3. Run tests: `./gradlew test`
4. Format code: `./gradlew spotlessApply`
5. Commit: `git commit -m "feat: add new feature"`
6. Push and create PR

### 11.3 Commit Message Format

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
feat: add new circuit breaker configuration
fix: resolve JWT token expiration issue
docs: update API documentation
test: add tests for UserService
refactor: improve error handling in controller
```

---

---
## 12. Support and Community

### 12.1 Getting Help

- **Issues**: Create a GitHub issue with details
- **Discussions**: Use GitHub Discussions for questions
- **Stack Overflow**: Tag questions with `spring-webflux`, `reactive-programming`

### 12.2 Reporting Bugs

Include:
1. Spring Boot version
2. JDK version
3. Profile used (dev/prod/test)
4. Steps to reproduce
5. Expected vs actual behavior
6. Relevant logs

---

**End of Instructions**
*For additional help, see testdata.md and postman.md*
*Last Updated: November 1, 2025*
