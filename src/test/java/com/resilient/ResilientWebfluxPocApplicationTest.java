package com.resilient;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("dev") // Ensure dev profile is active during tests
@SpringBootTest
class ResilientWebfluxPocApplicationTest {
    @Test
    void contextLoads() {
        // Happy path: context loads without errors
    }
}
