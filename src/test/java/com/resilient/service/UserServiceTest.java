package com.resilient.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;

import com.resilient.dto.UserRequest;
import com.resilient.dto.UserResponse;
import com.resilient.model.User;
import com.resilient.ports.UserAuditPort;
import com.resilient.ports.UserNotificationPort;
import com.resilient.ports.dto.AuditResult;
import com.resilient.ports.dto.NotificationResult;
import com.resilient.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class UserServiceTest {
    @Mock
    UserRepository userRepository;

    @Mock
    UserNotificationPort notificationPort;

    @Mock
    UserAuditPort auditPort;

    @InjectMocks
    UserService userService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void createUser_happyPath() {
        UserRequest request = new UserRequest("testuser", "test@example.com", "Test User");
        User user = User.create("testuser", "test@example.com", "Test User");
        when(userRepository.existsByUsername(request.username())).thenReturn(Mono.just(false));
        when(userRepository.existsByEmail(request.email())).thenReturn(Mono.just(false));
        when(userRepository.save(any(User.class))).thenReturn(Mono.just(user));
        when(notificationPort.sendWelcomeNotification(any(), any(), any()))
                .thenReturn(Mono.just(new NotificationResult("1", true, "notif-123")));
        when(auditPort.auditUserAction(any(), any(), any(), anyMap()))
                .thenReturn(Mono.just(new AuditResult("1", "success", "audit-123")));
        StepVerifier.create(userService.createUser(request))
                .expectNextMatches(r -> r.username().equals("testuser"))
                .verifyComplete();
    }

    @Test
    void findById_happyPath() {
        Long userId = 1L;
        User user = User.create("testUser", "test@example.com", "Test User");
        UserResponse expectedResponse = UserResponse.from(user);
        when(userRepository.findById(userId)).thenReturn(Mono.just(user));
        StepVerifier.create(userService.findById(userId))
                .expectNextMatches(response -> response.username().equals(expectedResponse.username()))
                .verifyComplete();
    }

    @Test
    void findAll_happyPath() {
        User user1 = User.create("user1", "user1@example.com", "User One");
        User user2 = User.create("user2", "user2@example.com", "User Two");
        when(userRepository.findAll()).thenReturn(Flux.just(user1, user2));
        StepVerifier.create(userService.findAll())
                .expectNextMatches(response -> response.username().equals("user1"))
                .expectNextMatches(response -> response.username().equals("user2"))
                .verifyComplete();
    }

    @Test
    void updateUserStatus_happyPath() {
        Long userId = 1L;
        User user = User.create("testUser", "test@example.com", "Test User");
        User updatedUser = user.withStatus(User.UserStatus.ACTIVE);
        UserResponse expectedResponse = UserResponse.from(updatedUser);
        when(userRepository.findById(userId)).thenReturn(Mono.just(user));
        when(userRepository.save(any(User.class))).thenReturn(Mono.just(updatedUser));
        when(notificationPort.sendStatusUpdate(any(), any(), any(), anyMap()))
                .thenReturn(Mono.just(new NotificationResult("1", true, "notif-123")));
        when(auditPort.auditUserAction(any(), any(), any(), anyMap()))
                .thenReturn(Mono.just(new AuditResult("1", "success", "audit-123")));
        StepVerifier.create(userService.updateUserStatus(userId, User.UserStatus.ACTIVE))
                .expectNextMatches(response -> response.username().equals(expectedResponse.username()))
                .verifyComplete();
    }

    @Test
    void deleteUser_happyPath() {
        Long userId = 1L;
        User user = User.create("testUser", "test@example.com", "Test User");
        when(userRepository.findById(userId)).thenReturn(Mono.just(user));
        when(userRepository.deleteById(userId)).thenReturn(Mono.empty());
        when(auditPort.auditUserAction(any(), any(), any(), anyMap()))
                .thenReturn(Mono.just(new AuditResult("1", "success", "audit-123")));
        StepVerifier.create(userService.deleteUser(userId)).expectNext(true).verifyComplete();
    }
}
