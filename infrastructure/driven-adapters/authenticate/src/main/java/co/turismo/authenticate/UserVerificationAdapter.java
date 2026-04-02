package co.turismo.authenticate;
 
import co.turismo.model.common.AppUrlConfig;
import co.turismo.model.notification.EmailMessage;
import co.turismo.model.notification.gateways.EmailGateway;
import co.turismo.model.user.EmailVerificationResult;
import co.turismo.model.user.gateways.UserRepository;
import co.turismo.model.user.gateways.UserVerificationGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
 
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;
 
@Component
@RequiredArgsConstructor
public class UserVerificationAdapter implements UserVerificationGateway {
 
    private static final Logger LOG = Logger.getLogger(UserVerificationAdapter.class.getName());
    private static final Duration VERIFICATION_TTL = Duration.ofHours(24);
    private static final SecureRandom RANDOM = new SecureRandom();
 
    private final UserRepository userRepository;
    private final EmailGateway emailGateway;
    private final AppUrlConfig appUrlConfig;
 
    @Override
    public Mono<EmailVerificationResult> sendVerificationEmail(String emailRaw) {
        String email = normalize(emailRaw);
        if (email == null || email.isBlank()) {
            return Mono.error(new IllegalArgumentException("Email requerido"));
        }
 
        return userRepository.isActiveByEmail(email)
                .filter(Boolean::booleanValue)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Usuario inexistente o bloqueado")))
                .then(userRepository.isEmailVerified(email))
                .flatMap(verified -> {
                    if (Boolean.TRUE.equals(verified)) {
                        LOG.log(Level.INFO, "Email {0} ya está verificado, omitiendo envío", email);
                        return Mono.just(new EmailVerificationResult(EmailVerificationResult.VerificationStatus.ALREADY_VERIFIED));
                    }
 
                    String token = generateToken();
                    String tokenHash = sha256(token);
                    OffsetDateTime expiresAt = OffsetDateTime.now().plus(VERIFICATION_TTL);
                    String link = buildVerificationLink(token);
 
                    LOG.log(Level.INFO, "Generando token de verificación para {0}", email);
 
                    return userRepository.saveEmailVerificationToken(email, tokenHash, expiresAt)
                            .then(emailGateway.sendEmail(new EmailMessage(
                                    email,
                                    "Verifica tu correo - Turismo App",
                                    buildVerificationHtml(link, token)
                            )))
                            .thenReturn(new EmailVerificationResult(EmailVerificationResult.VerificationStatus.SENT));
                });
    }
 
    private String buildVerificationLink(String token) {
        String frontendBase = appUrlConfig.frontendBaseUrl();
        if (frontendBase != null && !frontendBase.isBlank()) {
            String normalized = frontendBase.endsWith("/") ? frontendBase.substring(0, frontendBase.length() - 1) : frontendBase;
            return normalized + "/verify-email?token=" + token;
        }
 
        String backendBase = appUrlConfig.publicBaseUrl();
        if (backendBase == null || backendBase.isBlank()) {
            return "/api/auth/email/verify?token=" + token;
        }
        String normalized = backendBase.endsWith("/") ? backendBase.substring(0, backendBase.length() - 1) : backendBase;
        return normalized + "/api/auth/email/verify?token=" + token;
    }
 
    private String buildVerificationHtml(String link, String token) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #ffffff; margin: 0; padding: 0; color: #1a1a1a; -webkit-font-smoothing: antialiased; }
                    .container { max-width: 600px; margin: 40px auto; padding: 20px; }
                    .header { background: linear-gradient(135deg, #0d47a1, #6a1b9a, #c2185b); height: 6px; border-radius: 3px; margin-bottom: 40px; }
                    .brand { font-size: 20px; font-weight: 800; color: #6a1b9a; margin-bottom: 10px; letter-spacing: -0.5px; }
                    .content { text-align: left; }
                    .content h2 { font-size: 28px; font-weight: 700; color: #1a1a1a; margin-bottom: 20px; }
                    .content p { font-size: 16px; line-height: 1.6; color: #4a4a4a; margin-bottom: 30px; }
                    .cta-button { display: inline-block; padding: 16px 36px; background-color: #6a1b9a; color: #ffffff !important; text-decoration: none; border-radius: 8px; font-weight: 600; font-size: 16px; margin-bottom: 40px; }
                    .divider { height: 1px; background-color: #f0f0f0; margin: 40px 0; }
                    .token-section { background-color: #fafafa; border: 1px solid #eee; border-radius: 12px; padding: 30px; text-align: center; }
                    .token-label { font-size: 13px; font-weight: 600; color: #999; text-transform: uppercase; margin-bottom: 15px; letter-spacing: 1px; }
                    .token-wrapper { display: inline-block; padding: 10px 20px; background: #ffffff; border: 1px solid #e1bee7; border-radius: 6px; }
                    .token-value { font-family: 'SF Mono', 'Fira Code', 'Courier New', monospace; font-size: 24px; font-weight: 700; color: #c2185b; user-select: all; -webkit-user-select: all; }
                    .copy-hint { font-size: 12px; color: #666; margin-top: 15px; }
                    .footer { text-align: left; font-size: 13px; color: #999; margin-top: 40px; line-height: 1.5; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header"></div>
                    <div class="brand">Turismo App</div>
                    <div class="content">
                        <h2>Verifica tu cuenta</h2>
                        <p>Haz clic en el botón inferior para completar tu registro en la plataforma web.</p>
                        <a href="%s" class="cta-button">Confirmar correo electrónico</a>
                        
                        <div class="token-section">
                            <div class="token-label">Código de verificación manual</div>
                            <div class="token-wrapper">
                                <div class="token-value">%s</div>
                            </div>
                            <div class="copy-hint">Toca o haz doble clic sobre el código para copiarlo.</div>
                        </div>
                    </div>
                    <div class="footer">
                        Este código es válido por 24 horas.<br>
                        Si no has solicitado este correo, por favor ignóralo o contáctanos si crees que es un error.
                    </div>
                </div>
            </body>
            </html>
            """.formatted(link, token);
    }
 
    private String generateToken() {
        byte[] buf = new byte[32];
        RANDOM.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }
 
    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (Exception e) {
            throw new IllegalStateException("No fue posible generar hash", e);
        }
    }
 
    private String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }
}
