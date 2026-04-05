package co.turismo.model.auditLog.gateways;

import co.turismo.model.auditLog.AuditLog;
import reactor.core.publisher.Mono;

public interface AuditLogRepository {
    Mono<Void> registrar(AuditLog log);
}
