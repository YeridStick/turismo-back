package co.turismo.api.dto.email;

public record DebugEmailRequest(
        String email,
        String subject,
        String message,
        Boolean html
) {
}