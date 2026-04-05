package co.turismo.r2dbc.auditLog.repository;

import co.turismo.r2dbc.auditLog.entity.AuditLogData;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.query.ReactiveQueryByExampleExecutor;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface AuditLogAdapterRepository
        extends ReactiveCrudRepository<AuditLogData, Long>,
        ReactiveQueryByExampleExecutor<AuditLogData> {
    @Query("""
        INSERT INTO audit_log (tabla, registro_id, usuario_email, roles, fecha, datos)
        VALUES (:tabla, :registroId, :usuarioEmail, :roles::text[], NOW(), :datos::jsonb)
    """)
    Mono<Void> insertar(
            @Param("tabla") String tabla,
            @Param("registroId") Long registroId,
            @Param("usuarioEmail") String usuarioEmail,
            @Param("roles") String[] roles,
            @Param("datos") String datos
    );
}
