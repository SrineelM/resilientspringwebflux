# All Files in src Folder

## main/java/com/resilient
ResilientWebfluxPocApplication.java

### adapters
NotificationAdapter.java

### config
CompositeMeterRegistryConfig.java
DatabaseConfig.java
GracefulShutdownConfig.java
ReactorSchedulerConfig.java
RedisConfig.java
SchedulerConfig.java
SecurityConfig.java

### controller
DemoKafkaController.java
JwtAuthController.java
KafkaIntegrationController.java
ReactiveStreamController.java
SecureWebhookController.java
UserManagementController.java
WebhookController.java

### dto
ErrorResponse.java
UserRequest.java
UserResponse.java

### exception
BusinessException.java
GlobalExceptionHandler.java
UserAlreadyExistsException.java
UserNotFoundException.java

### filter
ReactiveCorrelationFilter.java

### health
CustomReactiveHealthIndicator.java

### messaging
ActiveMqConfig.java
ActiveMqConsumerPort.java
ActiveMqProducerPort.java
ActiveMqStubConsumer.java
ActiveMqStubProducer.java
KafkaConsumerConfig.java
KafkaConsumerPort.java
KafkaProducerConfig.java
KafkaProducerPort.java
KafkaStubConsumer.java
KafkaStubProducer.java
ReactiveActiveMqConsumer.java
ReactiveActiveMqProducer.java
ReactiveKafkaConsumer.java
ReactiveKafkaProducer.java

### model
User.java

### observability
CustomMetrics.java
TraceSpan.java
TraceSpanAspect.java

### ports
UserAuditPort.java
UserNotificationPort.java
dto/

### repository
UserRepository.java

### security
InMemoryReactiveRateLimiter.java
WebhookSignatureValidator.java
TokenBlacklistService.java
RedisTokenBlacklistService.java
RedisReactiveRateLimiter.java
ReactiveRateLimiter.java
ReactiveJwtAuthenticationManager.java
RateLimitingWebFilter.java
JwtUtil.java
InMemoryTokenBlacklistService.java

### service
UserService.java
external/

## main/resources
application-dev.yml
application-prod.yml
application-test.yml
application.yml
logback-spring.xml
schema.sql
