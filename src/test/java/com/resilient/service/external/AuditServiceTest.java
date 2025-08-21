package com.resilient.service.external;

import com.resilient.model.User;
import com.resilient.ports.dto.AuditEvent;
import com.resilient.ports.dto.AuditResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class AuditServiceTest {
    private AuditService auditService;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = Mockito.mock(MeterRegistry.class);
        Mockito.when(meterRegistry.counter(Mockito.anyString())).thenReturn(Mockito.mock(Counter.class));
        Mockito.when(meterRegistry.timer(Mockito.anyString())).thenReturn(Mockito.mock(Timer.class));
        auditService = new AuditService(meterRegistry);
        auditService.setBatchConcurrency(2); // Set batchConcurrency without reflection
    }

    private User createUserWithId(Long id) {
        return User.create("testuser", "test@example.com", "Test User").withId(id);
    }

    @Test
    void auditUserAction_happyPath() throws Exception {
        User user = createUserWithId(1L);
        Mono<AuditResult> result = auditService.auditUserAction("corr-123", "CREATE", user, Map.of());

        StepVerifier.create(result)
                .expectNextMatches(r -> r.isSuccess() && r.correlationId().equals("corr-123"))
                .verifyComplete();
    }

    @Test
    void auditBatchActions_happyPath() throws Exception {
        User user = createUserWithId(1L);
        AuditEvent event = new AuditEvent("corr-123", "CREATE", user, Map.of());
        Mono<List<AuditResult>> result = auditService.auditBatchActions(List.of(event));

        StepVerifier.create(result)
                .expectNextMatches(list -> list.size() == 1 && list.get(0).isSuccess())
                .verifyComplete();
    }
}
