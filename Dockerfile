# ---------------------------------------------
# Dockerfile for Resilient Spring WebFlux POC
# Builds a minimal, production-ready container
# ---------------------------------------------

# Use Eclipse Temurin JDK 17 as the base image
FROM eclipse-temurin:17-jre-alpine AS base

# Set working directory
WORKDIR /app

# Copy the built jar from the build stage (if using multi-stage) or from local build
# Uncomment the next line if you build with Gradle locally:
# COPY build/libs/resilientspringwebflux-*.jar app.jar

# If you want to build inside Docker, add a build stage here
# Example (uncomment if needed):
# FROM gradle:8.5.0-jdk17-alpine AS build
# COPY --chown=gradle:gradle . /home/gradle/src
# WORKDIR /home/gradle/src
# RUN gradle build --no-daemon
# COPY --from=build /home/gradle/src/build/libs/*.jar app.jar

# Expose the application port
EXPOSE 8080


# JVM flags for container awareness, fast GC, and startup
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=80 -Xshare:on -XX:+AlwaysPreTouch -XX:+UnlockExperimentalVMOptions -XX:+UseContainerSupport -Djava.security.egd=file:/dev/./urandom"

# Run the Spring Boot application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

# ---
# Usage:
# 1. Build the jar: ./gradlew clean build
# 2. Build the image: docker build -t resilientspringwebflux .
# 3. Run: docker run -p 8080:8080 resilientspringwebflux
# ---
