package com.resilient.security;

import com.resilient.repository.UserCredentialsRepository;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class ReactiveUserDetailsServiceImpl implements ReactiveUserDetailsService {

    private final UserCredentialsRepository repo;

    public ReactiveUserDetailsServiceImpl(UserCredentialsRepository repo) {
        this.repo = repo;
    }

    @Override
    public Mono<UserDetails> findByUsername(String username) {
        return repo.findByUsername(username).filter(UserCredentials::enabled).map(uc -> {
            List<String> roles = uc.roles() == null || uc.roles().isBlank()
                    ? List.of("USER")
                    : Arrays.stream(uc.roles().split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .collect(Collectors.toList());
            return User.withUsername(uc.username())
                    .password(uc.passwordHash())
                    .roles(roles.toArray(new String[0]))
                    .disabled(!uc.enabled())
                    .build();
        });
    }
}
