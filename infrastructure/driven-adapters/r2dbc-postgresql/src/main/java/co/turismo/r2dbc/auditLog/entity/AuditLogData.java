package co.turismo.r2dbc.auditLog.entity;

import io.r2dbc.postgresql.codec.Json;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Table("audit_log")
@Data
@Builder
public class AuditLogData {
    @Id
    private Long id;
    private String tabla;
    @Column("registro_id")
    private Long registroId;
    @Column("usuario_email")
    private String usuarioEmail;
    private String[] roles;
    private OffsetDateTime fecha;
    private Json datos;
}