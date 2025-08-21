package com.resilient.repository;

import com.resilient.model.User;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class UserRepositoryTest {
    @Test
    void findByUsername_happyPath() {
        UserRepository repo = Mockito.mock(UserRepository.class);
        User user = User.create("testuser", "test@example.com", "Test User");
        Mockito.when(repo.findByUsername("testuser")).thenReturn(Mono.just(user));
        StepVerifier.create(repo.findByUsername("testuser")).expectNext(user).verifyComplete();
    }
}
