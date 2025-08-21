Resilient Spring WebFlux Proof of Concept
AI Coding Agent Instructions

This project includes a .github/copilot-instructions.md file to guide AI coding agents (like GitHub Copilot) in understanding the architecture, workflows, conventions, and integration points specific to this codebase. This ensures automated coding agents generate code and suggestions that fit the project’s structure and standards, improving productivity and code quality. See .github/copilot-instructions.md for details.

Overview

This project demonstrates a scalable, secure, and observable Java application using Spring WebFlux, Resilience4j, OpenTelemetry, JWT authentication, webhooks, and reactive messaging with Kafka and ActiveMQ.

It models a modern, cloud-native, event-driven system with best practices for security, resilience, observability, and performance.

Key Features

Resilience: Circuit breakers, retries, bulkheads, and time limiters via Resilience4j.

Observability: Distributed tracing, metrics, and health checks integrated with OpenTelemetry, Zipkin, Prometheus, and Grafana.

Security: JWT authentication/authorization, secure webhooks with HMAC validation, rate limiting, input validation, and secure messaging.

Reactive Messaging: Kafka and ActiveMQ producers/consumers, reactive patterns for high throughput and non-blocking IO.

Webhooks: Secure, performant endpoints with HMAC verification and rate limiting.

Profiles & Environments: H2 for local/dev, PostgreSQL for production. Kafka/ActiveMQ configured for dev and prod separately.

Cloud-Native: Kubernetes-ready, graceful shutdown, and container-aware JVM tuning.

Prerequisites

JDK 17+

IntelliJ IDEA / Gradle / Git

Docker (for observability stack, Kafka, ActiveMQ, PostgreSQL in local/dev)

Optional: Postman or curl for API testing

Quick Start
Clone the repository
