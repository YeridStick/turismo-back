package co.turismo.api.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "TotpSetupResponse", description = "Información necesaria para enrolar el autenticador TOTP")
public record TotpSetupResponse(
        @Schema(description = "Secreto en Base32", example = "JBSWY3DPEHPK3PXP")
        String secretBase32,
        @Schema(description = "URI compatible con apps otpauth://", example = "otpauth://totp/Turismo:ana@example.com?secret=...")
        String otpAuthUri,
        @Schema(description = "QR como data URL listo para ser renderizado", example = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA...")
        String qrImage
) {
}
