package co.turismo.model.user;

import java.time.OffsetDateTime;

public record RecoveryStatus(
        String codeHash,
        OffsetDateTime expiresAt,
        Integer attempts,
        Integer maxAttempts
) {
}
