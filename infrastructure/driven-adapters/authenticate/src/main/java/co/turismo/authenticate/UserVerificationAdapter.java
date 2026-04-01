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
                                    "Verifica tu correo",
                                    buildVerificationHtml(link)
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
 
    private String buildVerificationHtml(String link) {
        return """
                <p>Hola,</p>
                <p>Para verificar tu correo, abre este enlace:</p>
                <p><a href="%s">%s</a></p>
                <p>Si no solicitaste esto, ignora el mensaje.</p>
                """.formatted(link, link);
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
