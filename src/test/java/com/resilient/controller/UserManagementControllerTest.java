package com.resilient.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.resilient.config.TestSecurityConfig;
import com.resilient.dto.UserRequest;
import com.resilient.dto.UserResponse;
import com.resilient.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@WebFluxTest(controllers = UserManagementController.class)
@Import(TestSecurityConfig.class)
class UserManagementControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    UserService userService;

    @Test
    @WithMockUser(
            username = "testuser",
            roles = {"ADMIN"})
    void createUser_happyPath() {
        UserRequest request = new UserRequest("abc", "abc@gmail.com", "Test User");
        UserResponse response = new UserResponse(
                1L,
                "testuser",
                "test@example.com",
                "Test User",
                com.resilient.model.User.UserStatus.ACTIVE,
                "2025-08-16T12:00:00Z",
                "2025-08-16T12:00:00Z");

        when(userService.createUser(any(UserRequest.class))).thenReturn(Mono.just(response));

        webTestClient
                .post()
                .uri("/api/users")
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .is2xxSuccessful()
                .expectBody()
                .jsonPath("$.id")
                .isEqualTo(1)
                .jsonPath("$.email")
                .isEqualTo("test@example.com")
                .jsonPath("$.status")
                .isEqualTo("ACTIVE");
    }

    @Test
    @WithMockUser(
            username = "testuser",
            roles = {"USER"})
    void createUser_invalidEmail() {
        UserRequest request = new UserRequest("abc", "invalid-email", "Test User");

        webTestClient
                .post()
                .uri("/api/users")
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isBadRequest();
    }
}
