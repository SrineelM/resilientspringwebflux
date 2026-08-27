# Resilient Spring WebFlux — Swagger & OpenAPI Documentation

This document provides complete documentation of the OpenAPI 3.0 / Swagger specifications and endpoints for the **Resilient Spring WebFlux POC**.

---

## 1. Accessing Interactive Swagger UI & OpenAPI Specs

When the application is running:

| Resource | URL | Description |
| :--- | :--- | :--- |
| **Swagger UI (Interactive)** | `http://localhost:8080/swagger-ui.html` | Interactive web dashboard for exploring and testing endpoints. |
| **OpenAPI 3 JSON Spec** | `http://localhost:8080/v3/api-docs` | Machine-readable OpenAPI 3.0 specification in JSON format. |
| **OpenAPI 3 YAML Spec** | `http://localhost:8080/v3/api-docs.yaml` | Machine-readable OpenAPI 3.0 specification in YAML format. |

---

## 2. Authentication & Security Schemes

The API uses **JWT (JSON Web Tokens)** for bearer authentication across secured endpoints, and **HMAC SHA-256 signatures** with anti-replay timestamps for webhooks.

### 2.1 Bearer Authentication (`bearerAuth`)
- **Type:** HTTP Bearer Token
- **Format:** JWT
- **Header:** `Authorization: Bearer <token>`
- **Obtaining Token:** Call `POST /api/auth/login` with valid credentials.

### 2.2 Webhook Signature & Anti-Replay (`webhookSignature` & `webhookTimestamp`)
- **Headers:**
  - `x-webhook-signature`: Hex-encoded HMAC-SHA256 hash of raw JSON payload using shared secret.
  - `x-webhook-timestamp`: Epoch millisecond timestamp (must be within ±5000 ms of server clock).

---

## 3. API Endpoints by Tag / Domain

### 🔐 Tag: Authentication (`/api/auth`)

#### 1. User Login & Token Generation
- **Endpoint:** `POST /api/auth/login`
- **Security:** Public (No Bearer token required)
- **Content-Type:** `application/json`
- **Request Body:**
  ```json
  {
    "username": "user",
    "password": "password"
  }
  ```
- **Responses:**
  - `200 OK`: Authentication succeeded. Returns JWT and expiration timestamp.
    ```json
    {
      "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
      "expires_in": "2026-08-27T09:30:00Z"
    }
    ```
  - `400 Bad Request`: Validation failure (blank username or password).
  - `401 Unauthorized`: Invalid username or password.

#### 2. User Logout & Token Invalidation
- **Endpoint:** `POST /api/auth/logout`
- **Security:** `bearerAuth` (Header `Authorization: Bearer <token>`)
- **Description:** Adds the active JWT to the token blacklist for its remaining TTL duration to prevent token reuse.
- **Responses:**
  - `204 No Content`: Token successfully invalidated.
  - `400 Bad Request`: Missing or malformed `Authorization` header.
  - `500 Internal Server Error`: Failed to update token blacklist.

#### 3. Refresh Active JWT Token
- **Endpoint:** `POST /api/auth/refresh`
- **Security:** `bearerAuth` (Header `Authorization: Bearer <token>`)
- **Description:** Validates and rotates an existing valid JWT, returning a new token with fresh expiration.
- **Responses:**
  - `200 OK`: Refreshed JWT returned.
    ```json
    {
      "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
      "expires_in": "2026-08-27T10:30:00Z"
    }
    ```
  - `401 Unauthorized`: Token is expired, blacklisted, or invalid.

---

### 👥 Tag: User Management (`/api/users`)

All endpoints in this group are protected by `bearerAuth` and monitored via Micrometer observations.

#### 1. Create User
- **Endpoint:** `POST /api/users`
- **Authorization:** `ROLE_ADMIN`
- **Resilience:** Circuit Breaker (`user-service`), Time Limiter (10s)
- **Request Body:**
  ```json
  {
    "username": "johndoe",
    "email": "johndoe@example.com",
    "fullName": "John Doe"
  }
  ```
- **Responses:**
  - `201 Created`: User created.
    ```json
    {
      "id": 1,
      "username": "johndoe",
      "email": "johndoe@example.com",
      "fullName": "John Doe",
      "status": "ACTIVE",
      "createdAt": "2026-08-27T08:00:00",
      "updatedAt": "2026-08-27T08:00:00"
    }
    ```
  - `400 Bad Request`: Validation failure.
  - `401 Unauthorized`: Missing or invalid JWT.
  - `403 Forbidden`: User lacks `ROLE_ADMIN`.
  - `503 Service Unavailable`: Circuit breaker open or operation timed out.

#### 2. Get User by ID
- **Endpoint:** `GET /api/users/{id}`
- **Authorization:** `ROLE_ADMIN` or `ROLE_USER`
- **Resilience:** Circuit Breaker (`user-service`), Timeout (5s)
- **Path Parameters:**
  - `id` (integer, min: 1): User ID
- **Responses:**
  - `200 OK`: User record returned.
  - `401 Unauthorized`: Missing or invalid JWT.
  - `404 Not Found`: User not found.
  - `503 Service Unavailable`: Circuit breaker fallback triggered.

#### 3. Update User
- **Endpoint:** `PUT /api/users/{id}`
- **Authorization:** `ROLE_ADMIN`
- **Path Parameters:**
  - `id` (integer, min: 1): User ID
- **Request Body:** `UserRequest`
- **Responses:**
  - `200 OK`: Updated user returned.
  - `400 Bad Request`: Validation failure.
  - `401 Unauthorized` / `403 Forbidden`
  - `404 Not Found`: User does not exist.

#### 4. List All Users
- **Endpoint:** `GET /api/users`
- **Authorization:** `ROLE_ADMIN`
- **Responses:**
  - `200 OK`: JSON array of `UserResponse` objects.

#### 5. Stream Users via Server-Sent Events (SSE)
- **Endpoint:** `GET /api/users/stream`
- **Produces:** `text/event-stream`
- **Authorization:** `ROLE_ADMIN`
- **Responses:**
  - `200 OK`: Real-time reactive stream of user objects with interval pacing.

#### 6. Search Users
- **Endpoint:** `GET /api/users/search?query={query}`
- **Authorization:** `ROLE_ADMIN` or `ROLE_USER`
- **Query Parameters:**
  - `query` (string, length: 2–50): Search keyword (matches username or email)
- **Responses:**
  - `200 OK`: Array of matching `UserResponse` objects.
  - `400 Bad Request`: Query parameter invalid.

#### 7. Update User Status
- **Endpoint:** `PUT /api/users/{id}/status?status={status}`
- **Authorization:** `ROLE_ADMIN`
- **Query Parameters:**
  - `status`: Enum (`ACTIVE`, `INACTIVE`, `SUSPENDED`)
- **Responses:**
  - `200 OK`: Updated `UserResponse`.
  - `404 Not Found`: User ID not found.

#### 8. Delete User
- **Endpoint:** `DELETE /api/users/{id}`
- **Authorization:** `ROLE_ADMIN`
- **Responses:**
  - `204 No Content`: User successfully deleted.
  - `404 Not Found`: User ID not found.

---

### 🌊 Tag: Reactive Streaming (`/stream`)

#### 1. Server-Sent Events (SSE) Stream
- **Endpoint:** `GET /stream/sse/users`
- **Produces:** `text/event-stream`
- **Description:** Emits 10 user items spaced 1 second apart with backpressure drop handling.
- **Responses:**
  - `200 OK`: Active SSE connection stream.

#### 2. Newline-Delimited JSON (NDJSON) Stream
- **Endpoint:** `GET /stream/ndjson/users`
- **Produces:** `application/x-ndjson`
- **Description:** Non-blocking streaming of JSON objects separated by newlines with buffer overflow handling.
- **Responses:**
  - `200 OK`: NDJSON stream.

#### 3. Chunked Binary File Stream with Caching
- **Endpoint:** `GET /stream/file`
- **Produces:** `application/octet-stream`
- **Request Headers (Optional for conditional GET):**
  - `If-None-Match`: Client cached ETag.
  - `If-Modified-Since`: Client cached modification epoch.
- **Responses:**
  - `200 OK`: Non-blocking chunked stream of DataBuffers with `ETag` and `Last-Modified` headers.
  - `304 Not Modified`: Cached file copy is up-to-date.
  - `404 Not Found`: Sample file not found.

---

### 🛡️ Tag: Secure Webhook (`/api/webhook`)

#### 1. Ingest Webhook Event
- **Endpoint:** `POST /api/webhook/event`
- **Consumes:** `application/json`
- **Security:** HMAC Signature (`x-webhook-signature`), Anti-Replay (`x-webhook-timestamp`)
- **Headers:**
  - `x-webhook-signature` (string, required): HMAC SHA-256 signature of request body.
  - `x-webhook-timestamp` (integer, required): Epoch ms timestamp (±5000 ms window).
  - `x-forwarded-for` / `x-real-ip` (optional): Source IP used for rate limiting.
- **Resilience:** Circuit Breaker (`webhook-processor`), Reactive Rate Limiter.
- **Responses:**
  - `202 Accepted`: Event passed signature and anti-replay verification and was queued for background processing.
  - `400 Bad Request`: Missing or expired timestamp header.
  - `401 Unauthorized`: Invalid HMAC signature or static secret.
  - `429 Too Many Requests`: Rate limit exceeded for requesting IP.

---

### 📬 Tag: Kafka Simulation (`/kafka`) *(Active in `local` profile)*

#### 1. Produce Kafka Message (Simulated)
- **Endpoint:** `POST /kafka/produce`
- **Consumes:** `application/json`
- **Request Body:**
  ```json
  {
    "content": "Order created with ID 98765"
  }
  ```
- **Responses:**
  - `202 Accepted`: Simulated message published to topic.
  - `400 Bad Request`: Message content blank or > 1024 chars.
  - `429 Too Many Requests`: Rate limit reached.

#### 2. Consume Kafka Message Stream (Simulated)
- **Endpoint:** `GET /kafka/consume`
- **Produces:** `text/event-stream`
- **Responses:**
  - `200 OK`: SSE stream emitting simulated Kafka events at 1-second intervals.

---

### 🔍 Tag: Observability & Baggage (`/demo`)

#### 1. Inspect Distributed Baggage & Incoming Headers
- **Endpoint:** `GET /demo/baggage`
- **Request Headers (Optional):**
  - `X-Correlation-Id`: Distributed trace correlation ID.
  - `X-User-Id`: Current user identifier.
  - `X-Tenant-Id`: Multi-tenant organization identifier.
- **Responses:**
  - `200 OK`:
    ```json
    {
      "baggage.correlationId": "c0a80101-8c4d-4b92-9e23-283948123abc",
      "baggage.userId": "user-42",
      "baggage.tenantId": "tenant-corp",
      "header.correlationId": "c0a80101-8c4d-4b92-9e23-283948123abc",
      "header.userId": "user-42",
      "header.tenantId": "tenant-corp"
    }
    ```

---

## 4. Data Transfer Objects (Schemas)

### `UserRequest`
| Field | Type | Required | Constraints | Description |
| :--- | :--- | :--- | :--- | :--- |
| `username` | `string` | Yes | 3–50 chars | Unique username |
| `email` | `string` | Yes | Valid email format | User's email |
| `fullName` | `string` | Yes | Not blank | Full name |

### `UserResponse`
| Field | Type | Example | Description |
| :--- | :--- | :--- | :--- |
| `id` | `integer (int64)` | `1` | Primary key ID |
| `username` | `string` | `"johndoe"` | Username |
| `email` | `string` | `"johndoe@example.com"` | Email address |
| `fullName` | `string` | `"John Doe"` | Full name |
| `status` | `string (enum)` | `"ACTIVE"` | `ACTIVE`, `INACTIVE`, `SUSPENDED` |
| `createdAt` | `string (ISO-8601)` | `"2026-08-27T08:00:00"` | Created timestamp |
| `updatedAt` | `string (ISO-8601)` | `"2026-08-27T08:30:00"` | Last updated timestamp |

### `ErrorResponse`
| Field | Type | Example | Description |
| :--- | :--- | :--- | :--- |
| `code` | `string` | `"VALIDATION_FAILED"` | Error classification code |
| `message` | `string` | `"The request payload failed validation"` | Human-readable explanation |
| `correlationId` | `string` | `"c0a80101-8c4d-4b92-9e23-283948123abc"` | Trace correlation ID |
| `timestamp` | `string (ISO-8601)` | `"2026-08-27T08:20:00Z"` | Error occurrence timestamp |
| `details` | `object` | `{"field": "email", "reason": "invalid"}` | Optional diagnostic metadata |
