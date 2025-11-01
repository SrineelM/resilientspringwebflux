# Postman Testing Guide
## API Endpoints and Sample Requests

**Base URL**: `http://localhost:8080`

---

## Postman Collection Setup

### Collection Variables

Set these variables in your Postman collection:

```
base_url: http://localhost:8080
jwt_token: (will be set after login)
correlation_id: {{$guid}}
```

---

## 1. Authentication Endpoints

### 1.1 Login
- **Method**: POST
- **URL**: `{{base_url}}/api/auth/login`
- **Headers**:
  - Content-Type: application/json
- **Body** (raw JSON):
```json
{
  "username": "admin",
  "password": "password"
}
```
- **Test Script**:
```javascript
if (pm.response.code === 200) {
    var jsonData = pm.response.json();
    pm.collectionVariables.set("jwt_token", jsonData.token);
}
```

### 1.2 Refresh Token
- **Method**: POST
- **URL**: `{{base_url}}/api/auth/refresh`
- **Headers**:
  - Authorization: Bearer {{jwt_token}}

### 1.3 Logout
- **Method**: POST
- **URL**: `{{base_url}}/api/auth/logout`
- **Headers**:
  - Authorization: Bearer {{jwt_token}}

---

## 2. User Management Endpoints

### 2.1 Create User
- **Method**: POST
- **URL**: `{{base_url}}/api/users`
- **Headers**:
  - Authorization: Bearer {{jwt_token}}
  - Content-Type: application/json
  - X-Correlation-Id: {{correlation_id}}
- **Body**:
```json
{
  "username": "johndoe",
  "email": "john.doe@example.com",
  "fullName": "John Doe"
}
```

### 2.2 Get User by ID
- **Method**: GET
- **URL**: `{{base_url}}/api/users/1`
- **Headers**:
  - Authorization: Bearer {{jwt_token}}

### 2.3 Get All Users
- **Method**: GET
- **URL**: `{{base_url}}/api/users`
- **Headers**:
  - Authorization: Bearer {{jwt_token}}

### 2.4 Search Users
- **Method**: GET
- **URL**: `{{base_url}}/api/users/search?query=john`
- **Headers**:
  - Authorization: Bearer {{jwt_token}}

### 2.5 Update User Status
- **Method**: PUT
- **URL**: `{{base_url}}/api/users/1/status`
- **Headers**:
  - Authorization: Bearer {{jwt_token}}
  - Content-Type: application/json
- **Body**:
```json
{
  "status": "INACTIVE"
}
```

### 2.6 Delete User
- **Method**: DELETE
- **URL**: `{{base_url}}/api/users/1`
- **Headers**:
  - Authorization: Bearer {{jwt_token}}

---

## 3. Webhook Endpoints

### 3.1 Receive Webhook (with HMAC)
- **Method**: POST
- **URL**: `{{base_url}}/api/webhook/receive`
- **Headers**:
  - Authorization: Bearer {{jwt_token}}
  - Content-Type: application/json
  - X-Webhook-Secret: devwebhooksecret
  - X-Webhook-Signature: (calculate HMAC-SHA256)
- **Body**:
```json
{
  "event": "user.created",
  "user_id": "12345",
  "timestamp": "2024-11-01T12:00:00Z"
}
```

**Pre-request Script** (to calculate HMAC):
```javascript
var CryptoJS = require('crypto-js');
var secret = 'devwebhookhmacsecret';
var body = pm.request.body.raw;
var hash = CryptoJS.HmacSHA256(body, secret);
var base64 = CryptoJS.enc.Base64.stringify(hash);
pm.request.headers.add({key: 'X-Webhook-Signature', value: base64});
```

---

## 4. Health and Monitoring

### 4.1 Health Check
- **Method**: GET
- **URL**: `{{base_url}}/actuator/health`

### 4.2 Prometheus Metrics
- **Method**: GET
- **URL**: `{{base_url}}/actuator/prometheus`

### 4.3 Application Info
- **Method**: GET
- **URL**: `{{base_url}}/actuator/info`

---

## 5. Kafka Demo Endpoints

### 5.1 Send Kafka Message
- **Method**: POST
- **URL**: `{{base_url}}/api/kafka/send`
- **Headers**:
  - Authorization: Bearer {{jwt_token}}
  - Content-Type: application/json
- **Body**:
```json
{
  "topic": "demo-topic",
  "key": "user-123",
  "message": "Test message from Postman"
}
```

---

## 6. Reactive Stream Endpoints

### 6.1 Server-Sent Events (SSE)
- **Method**: GET
- **URL**: `{{base_url}}/api/stream/events`
- **Headers**:
  - Authorization: Bearer {{jwt_token}}
  - Accept: text/event-stream

### 6.2 Number Stream
- **Method**: GET
- **URL**: `{{base_url}}/api/stream/numbers?count=10`
- **Headers**:
  - Authorization: Bearer {{jwt_token}}

---

## 7. Test Scenarios

### Scenario 1: Complete User Lifecycle
1. **Login** → Save JWT token
2. **Create User** → Note user ID from response
3. **Get User** → Verify created user
4. **Update User Status** → Change to INACTIVE
5. **Get User** → Verify status changed
6. **Delete User** → Remove user
7. **Get User** → Should return 404

### Scenario 2: Error Handling
1. **Create User with Invalid Email** → Expect 400
2. **Create Duplicate User** → Expect 409
3. **Get Non-existent User** → Expect 404
4. **Access Endpoint Without JWT** → Expect 401

### Scenario 3: Rate Limiting
1. Send 15 rapid webhook requests
2. Verify 429 (Too Many Requests) after threshold
3. Wait 60 seconds
4. Retry → Should succeed

---

## 8. Common Test Assertions

### Success Response (200 OK)
```javascript
pm.test("Status code is 200", function () {
    pm.response.to.have.status(200);
});

pm.test("Response has correct structure", function () {
    var jsonData = pm.response.json();
    pm.expect(jsonData).to.have.property('username');
    pm.expect(jsonData).to.have.property('email');
});
```

### Unauthorized (401)
```javascript
pm.test("Status code is 401 Unauthorized", function () {
    pm.response.to.have.status(401);
});
```

### Validation Error (400)
```javascript
pm.test("Status code is 400 Bad Request", function () {
    pm.response.to.have.status(400);
});

pm.test("Error message is present", function () {
    var jsonData = pm.response.json();
    pm.expect(jsonData).to.have.property('message');
});
```

---

## 9. Environment Setup

### Development Environment
```json
{
  "base_url": "http://localhost:8080",
  "username": "admin",
  "password": "password",
  "webhook_secret": "devwebhooksecret",
  "webhook_hmac_secret": "devwebhookhmacsecret"
}
```

### Production Environment
```json
{
  "base_url": "https://api.resilient.com",
  "username": "{{PROD_USERNAME}}",
  "password": "{{PROD_PASSWORD}}",
  "webhook_secret": "{{PROD_WEBHOOK_SECRET}}",
  "webhook_hmac_secret": "{{PROD_HMAC_SECRET}}"
}
```

---

## 10. Importing the Collection

### Option 1: Manual Creation
Follow the endpoint definitions above and create each request manually in Postman.

### Option 2: Export/Import JSON
After creating the collection, export it:
1. Right-click collection → Export
2. Choose Collection v2.1 format
3. Share the JSON file with team members

---

**End of Postman Guide**
