package co.turismo.r2dbc.usersRepository.dto;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class RecoveryStatusRow {
    private String recoveryCodeHash;
    private OffsetDateTime recoveryExpiresAt;
    private Integer recoveryAttempts;
    private Integer recoveryMaxAttempts;
}
