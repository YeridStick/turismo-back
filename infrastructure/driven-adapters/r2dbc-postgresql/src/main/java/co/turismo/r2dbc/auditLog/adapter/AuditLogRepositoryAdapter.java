package co.turismo.r2dbc.auditLog.adapter;

import co.turismo.model.auditLog.AuditLog;
import co.turismo.model.auditLog.gateways.AuditLogRepository;
import co.turismo.r2dbc.auditLog.entity.AuditLogData;
import co.turismo.r2dbc.auditLog.repository.AuditLogAdapterRepository;
import co.turismo.r2dbc.helper.ReactiveAdapterOperations;
import co.turismo.r2dbc.usersRepository.adapter.UserRepositoryAdapter;
import org.reactivecommons.utils.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public class AuditLogRepositoryAdapter
        extends ReactiveAdapterOperations<AuditLog, AuditLogData, Long, AuditLogAdapterRepository>
implements AuditLogRepository {

    private static final Logger LOG = LoggerFactory.getLogger(AuditLogRepositoryAdapter.class);

    public AuditLogRepositoryAdapter(AuditLogAdapterRepository repository, ObjectMapper mapper) {
        super(repository, mapper, d -> mapper.map(d, AuditLog.class));
    }

    @Override
    public Mono<Void> registrar(AuditLog log) {
        return Mono.fromCallable(() -> mapper.map(log.getDatos(), String.class))
                .flatMap(json -> repository.insertar(
                        log.getTabla(),
                        log.getRegistroId(),
                        log.getUsuarioEmail(),
                        log.getRoles(),
                        json
                ))
                .onErrorResume(e -> {
                    LOG.error("Error al registrar auditaria: ", e.getMessage());
                    return Mono.empty();
                });
    }
}
