package co.turismo.model.user;

import java.time.OffsetDateTime;

public record RecoveryTokenStatus(
        String email,
        OffsetDateTime expiresAt,
        Integer attempts,
        Integer maxAttempts
) {
}
