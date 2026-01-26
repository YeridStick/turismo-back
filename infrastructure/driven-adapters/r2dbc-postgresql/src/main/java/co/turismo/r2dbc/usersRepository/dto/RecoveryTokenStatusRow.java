package co.turismo.r2dbc.usersRepository.dto;

import org.springframework.data.relational.core.mapping.Column;

import java.time.OffsetDateTime;

public class RecoveryTokenStatusRow {
    @Column("email")
    private String email;

    @Column("recovery_code_hash")
    private String recoveryCodeHash;

    @Column("recovery_expires_at")
    private OffsetDateTime recoveryExpiresAt;

    @Column("recovery_attempts")
    private Integer recoveryAttempts;

    @Column("recovery_max_attempts")
    private Integer recoveryMaxAttempts;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRecoveryCodeHash() {
        return recoveryCodeHash;
    }

    public void setRecoveryCodeHash(String recoveryCodeHash) {
        this.recoveryCodeHash = recoveryCodeHash;
    }

    public OffsetDateTime getRecoveryExpiresAt() {
        return recoveryExpiresAt;
    }

    public void setRecoveryExpiresAt(OffsetDateTime recoveryExpiresAt) {
        this.recoveryExpiresAt = recoveryExpiresAt;
    }

    public Integer getRecoveryAttempts() {
        return recoveryAttempts;
    }

    public void setRecoveryAttempts(Integer recoveryAttempts) {
        this.recoveryAttempts = recoveryAttempts;
    }

    public Integer getRecoveryMaxAttempts() {
        return recoveryMaxAttempts;
    }

    public void setRecoveryMaxAttempts(Integer recoveryMaxAttempts) {
        this.recoveryMaxAttempts = recoveryMaxAttempts;
    }
}
