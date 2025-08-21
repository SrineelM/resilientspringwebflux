package com.resilient.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.resilient.dto.UserRequest;
import com.resilient.dto.UserResponse;
import com.resilient.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(
        properties = {
            "management.endpoint.health.validate-group-membership=false",
            "logging.level.org.springframework.security=DEBUG"
        })
class UserManagementControllerTest {
    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    UserService userService;

    @Test
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
                .is2xxSuccessful();
    }
}
