package com.resilient.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
// ...existing imports...
import reactor.test.StepVerifier;
import reactor.core.publisher.Flux;

import com.resilient.config.TestSecurityConfig;
import com.resilient.dto.UserResponse;

import java.time.Duration;

@WebFluxTest(controllers = ReactiveStreamController.class)
@Import(TestSecurityConfig.class)
@ActiveProfiles("dev")
class ReactiveStreamControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void streamUsersSse_happyPath() {
        Flux<UserResponse> events = webTestClient
                .get()
                .uri("/stream/sse/users")
                .accept(org.springframework.http.MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .returnResult(UserResponse.class)
                .getResponseBody();

        StepVerifier.create(events.take(3))
                .expectNextMatches(user -> 
                    user.username().startsWith("User")
                )
                .expectNextMatches(user -> 
                    user.email().contains("@example.com")
                )
                .expectNextMatches(user -> 
                    user.fullName().startsWith("User ")
                )
                .thenCancel()
                .verify(Duration.ofSeconds(5));
    }

    @Test
    void streamUsersSse_verifyMultipleEvents() {
    Flux<UserResponse> events = webTestClient
        .get()
        .uri("/stream/sse/users")
        .accept(org.springframework.http.MediaType.TEXT_EVENT_STREAM)
        .exchange()
        .returnResult(UserResponse.class)
        .getResponseBody();

    StepVerifier.create(events.take(5))
        .expectNextCount(5)
        .thenCancel()
        .verify(Duration.ofSeconds(5));
    }

    @Test
    void streamUsersNdjson_happyPath() {
        Flux<UserResponse> users = webTestClient
                .get()
                .uri("/stream/ndjson/users")
                .accept(org.springframework.http.MediaType.APPLICATION_NDJSON)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(org.springframework.http.MediaType.APPLICATION_NDJSON)
                .returnResult(UserResponse.class)
                .getResponseBody();

        StepVerifier.create(users.take(5))
                .expectNextMatches(user -> 
                    user.username().startsWith("User") && 
                    user.email().contains("@example.com")
                )
                .expectNextCount(4)
                .thenCancel()
                .verify(Duration.ofSeconds(3));
    }

    @Test
    void streamUsersNdjson_verifyCompleteStream() {
        Flux<UserResponse> users = webTestClient
                .get()
                .uri("/stream/ndjson/users")
                .accept(org.springframework.http.MediaType.APPLICATION_NDJSON)
                .exchange()
                .returnResult(UserResponse.class)
                .getResponseBody();

        StepVerifier.create(users)
                .expectNextCount(10)
                .verifyComplete();
    }

    @Test
    void streamFile_happyPath() {
        webTestClient
                .get()
                .uri("/stream/file")
                .accept(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM)
                .expectHeader().exists("ETag")
                .expectHeader().exists("Last-Modified")
                .expectBody()
                .consumeWith(response -> {
                    // Verify that we get some content
                    byte[] body = response.getResponseBody();
                    org.junit.jupiter.api.Assertions.assertNotNull(body);
                    org.junit.jupiter.api.Assertions.assertTrue(body.length > 0);
                });
    }

    @Test
    void streamFile_withConditionalHeaders() {
    // Test with If-None-Match header (should return 200 since ETag won't match)
    webTestClient
        .get()
        .uri("/stream/file")
        .header("If-None-Match", "\"invalid-etag\"")
        .accept(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM)
        .exchange()
        .expectStatus().isOk()
        .expectHeader().exists("ETag")
        .expectHeader().exists("Last-Modified");

    // Test with If-Modified-Since header (should return 200 since date won't match)
    webTestClient
        .get()
        .uri("/stream/file")
        .header("If-Modified-Since", "Wed, 21 Oct 2015 07:28:00 GMT")
        .accept(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM)
        .exchange()
        .expectStatus().isOk()
        .expectHeader().exists("ETag")
        .expectHeader().exists("Last-Modified");
    }
// ...existing code...
}