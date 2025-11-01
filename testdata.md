# Test Data for Resilient Spring WebFlux POC

This document contains all the test data needed for manual and automated testing of the application.

---

## Table of Contents
1. [User Test Data](#user-test-data)
2. [JWT Token Examples](#jwt-token-examples)
3. [Authentication Requests](#authentication-requests)
4. [Webhook Test Data](#webhook-test-data)
5. [Kafka Message Examples](#kafka-message-examples)
6. [Database Seed Data](#database-seed-data)

---

## 1. User Test Data

### Valid Users for Testing

#### User 1: Admin User
```json
{
  "username": "admin",
  "email": "admin@resilient.com",
  "fullName": "Admin User",
  "status": "ACTIVE"
}
```

#### User 2: Regular User
```json
{
  "username": "johndoe",
  "email": "john.doe@example.com",
  "fullName": "John Doe",
  "status": "ACTIVE"
}
```

#### User 3: Inactive User
```json
{
  "username": "janedoe",
  "email": "jane.doe@example.com",
  "fullName": "Jane Doe",
  "status": "INACTIVE"
}
```

### Invalid User Data (for validation testing)

#### Missing Required Fields
```json
{
  "username": "",
  "email": "invalid@example.com"
}
```

#### Invalid Email Format
```json
{
  "username": "testuser",
  "email": "not-an-email",
  "fullName": "Test User"
}
```

#### Duplicate Username
```json
{
  "username": "admin",  // Already exists
  "email": "newemail@example.com",
  "fullName": "Another Admin"
}
```

---

## 2. JWT Token Examples

### How to Generate JWT Tokens

**Step 1: Login to get a token**
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "password"
  }'
```

**Response**:
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlzcyI6Imh0dHBzOi8vYXV0aC5kZXYucmVzaWxpZW50LmNvbSIsImlhdCI6MTY5ODc2MzIwMCwiZXhwIjoxNjk4NzY2ODAwLCJhdWQiOlsicmVzaWxpZW50LWFwcCIsImFkbWluLXBvcnRhbCJdLCJyb2xlcyI6WyJST0xFX0FETUlOIiwiUk9MRV9VU0VSIl19.YourActualSignatureHere",
  "expires_in": "2024-11-01T12:00:00Z"
}
```

### Sample JWT Token Structure

**Header**:
```json
{
  "alg": "HS256"
}
```

**Payload**:
```json
{
  "sub": "admin",
  "iss": "https://auth.dev.resilient.com",
  "iat": 1698763200,
  "exp": 1698766800,
  "aud": ["resilient-app", "admin-portal"],
  "roles": ["ROLE_ADMIN", "ROLE_USER"],
  "type": "access",
  "client_id": "web-app",
  "version": 1
}
```

### Using JWT in Requests

Add to Authorization header:
```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiI...
```

---

## 3. Authentication Requests

### Login Request
```bash
# POST /api/auth/login
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "password"
  }'
```

**Expected Response (200 OK)**:
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "expires_in": "2024-11-01T13:00:00Z"
}
```

**Error Response (401 Unauthorized)**:
```json
{
  "error": "Invalid credentials"
}
```

### Refresh Token Request
```bash
# POST /api/auth/refresh
curl -X POST http://localhost:8080/api/auth/refresh \
  -H "Authorization: Bearer YOUR_EXISTING_TOKEN"
```

**Expected Response (200 OK)**:
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "expires_in": "2024-11-01T14:00:00Z"
}
```

### Logout Request
```bash
# POST /api/auth/logout
curl -X POST http://localhost:8080/api/auth/logout \
  -H "Authorization: Bearer YOUR_TOKEN"
```

**Expected Response (204 No Content)** - empty body

---

## 4. Webhook Test Data

### Valid Webhook Request with HMAC Signature

#### Generate HMAC Signature (Node.js example):
```javascript
const crypto = require('crypto');

const payload = JSON.stringify({
  event: "user.created",
  user_id": "12345",
  timestamp": "2024-11-01T12:00:00Z"
});

const secret = "devwebhookhmacsecret";
const signature = crypto
  .createHmac('sha256', secret)
  .update(payload)
  .digest('base64');

console.log('X-Webhook-Signature:', signature);
```

#### Sample Request:
```bash
curl -X POST http://localhost:8080/api/webhook/receive \
  -H "Content-Type: application/json" \
  -H "X-Webhook-Secret: devwebhooksecret" \
  -H "X-Webhook-Signature: CALCULATED_HMAC_SIGNATURE" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{
    "event": "user.created",
    "user_id": "12345",
    "timestamp": "2024-11-01T12:00:00Z"
  }'
```

### HMAC Calculation (Python example):
```python
import hmac
import hashlib
import base64
import json

payload = json.dumps({
    "event": "user.created",
    "user_id": "12345",
    "timestamp": "2024-11-01T12:00:00Z"
})

secret = b"devwebhookhmacsecret"
signature = base64.b64encode(
    hmac.new(secret, payload.encode(), hashlib.sha256).digest()
).decode()

print(f"X-Webhook-Signature: {signature}")
```

### Expected Responses

**Success (200 OK)**:
```json
{
  "status": "received",
  "message": "Webhook processed successfully"
}
```

**Invalid Signature (401 Unauthorized)**:
```json
{
  "error": "Invalid webhook signature"
}
```

**Rate Limit Exceeded (429 Too Many Requests)**:
```json
{
  "error": "Too many requests"
}
```

---

## 5. Kafka Message Examples

### Send Kafka Message
```bash
curl -X POST http://localhost:8080/api/kafka/send \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "topic": "demo-topic",
    "key": "user-123",
    "message": "User account created"
  }'
```

### Sample Kafka Message with Headers
```json
{
  "key": "user-123",
  "value": {
    "event_type": "USER_CREATED",
    "user_id": "123",
    "timestamp": "2024-11-01T12:00:00Z",
    "data": {
      "username": "johndoe",
      "email": "john@example.com"
    }
  },
  "headers": {
    "correlationId": "550e8400-e29b-41d4-a716-446655440000",
    "traceparent": "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"
  }
}
```

---

## 6. Database Seed Data

### Insert Test Users (SQL)
```sql
-- Insert test users into the database
INSERT INTO users (username, email, full_name, status, created_at, updated_at)
VALUES
  ('admin', 'admin@resilient.com', 'Admin User', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('johndoe', 'john.doe@example.com', 'John Doe', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('janedoe', 'jane.doe@example.com', 'Jane Doe', 'INACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
```

### Insert User Credentials
```sql
-- Password is 'password' hashed with BCrypt
INSERT INTO user_credentials (username, password_hash, created_at)
VALUES
  ('admin', '{bcrypt}$2a$10$Dow1jZz8LRx.9Z/9Bf4fEuCcoYI9Y1VtWc2Sbl3hM3Pph7XnJbI1G', CURRENT_TIMESTAMP),
  ('johndoe', '{bcrypt}$2a$10$Dow1jZz8LRx.9Z/9Bf4fEuCcoYI9Y1VtWc2Sbl3hM3Pph7XnJbI1G', CURRENT_TIMESTAMP);
```

---

## 7. Correlation and Tracing Headers

### Request with Correlation Headers
```bash
curl -X GET http://localhost:8080/api/users \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "X-Correlation-Id: 550e8400-e29b-41d4-a716-446655440000" \
  -H "X-User-Id: johndoe" \
  -H "X-Tenant-Id: acme-corp"
```

### W3C Trace Context Header
```
traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01
```

---

## 8. Complete User Management Flow

### Step 1: Create a User
```bash
curl -X POST http://localhost:8080/api/users \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -H "X-Correlation-Id: $(uuidgen)" \
  -d '{
    "username": "newuser",
    "email": "new.user@example.com",
    "fullName": "New User"
  }'
```

### Step 2: Get User by ID
```bash
curl -X GET http://localhost:8080/api/users/1 \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

### Step 3: Search Users
```bash
curl -X GET "http://localhost:8080/api/users/search?query=john" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

### Step 4: Update User Status
```bash
curl -X PUT http://localhost:8080/api/users/1/status \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "status": "INACTIVE"
  }'
```

### Step 5: Delete User
```bash
curl -X DELETE http://localhost:8080/api/users/1 \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

---

## 9. Environment Variables for Testing

### Required Environment Variables
```bash
# JWT Configuration
export JWT_SECRET="your-super-secret-jwt-key-min-256-bits-aabbccddeeff00112233445566778899"
export JWT_ISSUER="https://auth.resilient.com"
export JWT_AUDIENCE="resilient-app,admin-portal"

# Webhook Configuration
export WEBHOOK_SECRET="your-webhook-secret"
export WEBHOOK_HMAC_SECRET="your-hmac-secret"

# Database (for production)
export PROD_DB_HOST="localhost"
export PROD_DB_NAME="resilient_db"
export PROD_DB_USERNAME="db_user"
export PROD_DB_PASSWORD="db_password"

# Kafka (for production)
export KAFKA_BOOTSTRAP_SERVERS="localhost:9092"

# Redis (for production)
export REDIS_HOST="localhost"
export REDIS_PORT="6379"
```

---

## 10. Testing Scenarios

### Scenario 1: Successful User Registration and Login
1. Create a new user via POST /api/users
2. Login with the new user credentials
3. Verify JWT token is returned
4. Use the token to access protected endpoints

### Scenario 2: Rate Limiting Test
1. Send 10+ rapid requests to /api/webhook/receive
2. Verify 429 Too Many Requests after threshold
3. Wait for rate limit window to reset
4. Verify requests are allowed again

### Scenario 3: Circuit Breaker Test
1. Trigger multiple failures in userService
2. Verify circuit breaker opens
3. Verify fallback responses are returned
4. Wait for half-open state
5. Verify circuit breaker closes after successful calls

### Scenario 4: Distributed Tracing Test
1. Send request with X-Correlation-Id header
2. Check logs for correlation ID propagation
3. Verify Zipkin shows the trace (if enabled)
4. Verify baggage values in downstream services

---

## 11. Common Test Passwords

All test users use the same password for simplicity:
- **Password**: `password`
- **BCrypt Hash**: `{bcrypt}$2a$10$Dow1jZz8LRx.9Z/9Bf4fEuCcoYI9Y1VtWc2Sbl3hM3Pph7XnJbI1G`

**⚠️ WARNING**: These are for development/testing only. Never use in production!

---

## 12. Postman Collection Variables

If using Postman, set these collection variables:

```json
{
  "base_url": "http://localhost:8080",
  "jwt_token": "{{login_token}}",
  "correlation_id": "{{$guid}}",
  "webhook_secret": "devwebhooksecret",
  "webhook_hmac_secret": "devwebhookhmacsecret"
}
```

---

**End of Test Data Documentation**
*Last Updated: November 1, 2025*
