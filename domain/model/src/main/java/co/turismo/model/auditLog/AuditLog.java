package co.turismo.model.auditLog;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

@Builder
@Getter
public class AuditLog {
    private Long id;
    private String tabla;
    private Long registroId;
    private String usuarioEmail;
    private String[] roles;
    private OffsetDateTime fecha;
    private Object datos;
}