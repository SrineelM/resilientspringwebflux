package com.resilient.ports;

import com.resilient.model.User;
import com.resilient.ports.dto.AuditEvent;
import com.resilient.ports.dto.AuditResult;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

/** Domain-specific audit port for user auditing operations. */
public interface UserAuditPort {
    Mono<AuditResult> auditUserAction(String correlationId, String action, User user, Map<String, Object> context);

    Mono<List<AuditResult>> auditBatchActions(List<AuditEvent> events);
}
