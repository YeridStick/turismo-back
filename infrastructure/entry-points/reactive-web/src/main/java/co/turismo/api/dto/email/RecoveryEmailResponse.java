package co.turismo.api.dto.email;

public record RecoveryEmailResponse(
        String link,
        String token
) {
}