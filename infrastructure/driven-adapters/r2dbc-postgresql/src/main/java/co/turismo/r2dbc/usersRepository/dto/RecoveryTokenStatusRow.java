package co.turismo.r2dbc.usersRepository.dto;

import java.time.OffsetDateTime;

public class RecoveryTokenStatusRow {
    private String email;
    private String recoveryCodeHash;
    private OffsetDateTime recoveryExpiresAt;
    private Integer recoveryAttempts;
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
