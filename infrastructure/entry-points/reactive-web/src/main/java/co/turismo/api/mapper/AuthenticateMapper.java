package co.turismo.api.mapper;

import co.turismo.api.dto.auth.EmailVerificationResponse;
import co.turismo.api.dto.auth.JwtTokenResponse;
import co.turismo.api.dto.auth.TotpSetupResponse;
import co.turismo.api.dto.auth.TotpStatusResponse;
import co.turismo.api.dto.common.SimpleMessageResponse;
import co.turismo.api.util.QrCodeUtil;
import co.turismo.model.notification.EmailMessage;
import co.turismo.model.user.EmailVerificationResult;

import java.util.Map;

public class AuthenticateMapper {
    private AuthenticateMapper() {}

    public static TotpSetupResponse toTotpSetupResponse(String secretBase32, String otpAuthUri) {
        String qrImage = QrCodeUtil.generateQrDataUrl(otpAuthUri);
        return new TotpSetupResponse(secretBase32, otpAuthUri, qrImage);
    }

    public static TotpStatusResponse toTotpStatusResponse(Boolean enabled) {
        return new TotpStatusResponse(enabled);
    }

    public static JwtTokenResponse toJwtTokenResponse(String token) {
        return new JwtTokenResponse(token);
    }

    public static SimpleMessageResponse toSimpleMessageResponse(String message) {
        return new SimpleMessageResponse(message);
    }

    public static EmailVerificationResponse toEmailVerificationResponse(EmailVerificationResult result) {
        String status = result.status().name().toLowerCase();

        String message = result.status() == EmailVerificationResult.VerificationStatus.ALREADY_VERIFIED
                ? "Correo ya estaba verificado"
                : "Correo de verificacion enviado";

        return new EmailVerificationResponse(status, message);
    }

    public static EmailMessage toRecoveryEmail(String email, String link, String token) {
        String html = "<p>Hola,</p>"
                + "<p>Link: <a href=\"" + link + "\">recuperar</a></p>"
                + "<p>O usa este código: <strong>" + token + "</strong></p>";

        return new EmailMessage(
                email,
                "Recupera tu cuenta",
                html
        );
    }

    public static Map<String, String> toRecoveryPayload(String link, String token) {
        return Map.of(
                "message", "Enlace enviado",
                "link", link,
                "token", token
        );
    }
}
