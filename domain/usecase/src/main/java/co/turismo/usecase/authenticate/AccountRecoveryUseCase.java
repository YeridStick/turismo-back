package co.turismo.usecase.authenticate;

import co.turismo.model.authenticationsession.gateways.TotpSecretRepository;
import co.turismo.model.common.AppUrlConfig;
import co.turismo.model.notification.EmailMessage;
import co.turismo.model.notification.gateways.EmailGateway;
import co.turismo.model.security.gateways.PasswordHasher;
import co.turismo.model.user.RecoveryStatus;
import co.turismo.model.user.RecoveryTokenStatus;
import co.turismo.model.user.gateways.UserRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;
import java.util.logging.Level;
import java.util.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;

@RequiredArgsConstructor
public class AccountRecoveryUseCase {

    private static final Logger LOG = Logger.getLogger(AccountRecoveryUseCase.class.getName());
    private static final Duration VERIFICATION_TTL = Duration.ofHours(24);
    private static final Duration RECOVERY_TTL = Duration.ofMinutes(10);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final TotpSecretRepository totpSecretRepository;
    private final EmailGateway emailGateway;
    private final AppUrlConfig appUrlConfig;
    private final PasswordHasher passwordHasher;

    public Mono<Void> sendVerificationEmail(String emailRaw) {
        return requestEmailVerification(emailRaw).then();
    }

    public Mono<EmailVerificationResult> requestEmailVerification(String emailRaw) {
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
                        return Mono.just(new EmailVerificationResult(VerificationStatus.ALREADY_VERIFIED));
                    }
                    String token = generateToken();
                    String tokenHash = sha256(token);
                    OffsetDateTime expiresAt = OffsetDateTime.now().plus(VERIFICATION_TTL);
                    String link = buildVerificationLink(token);

                    return userRepository.saveEmailVerificationToken(email, tokenHash, expiresAt)
                            .then(emailGateway.sendEmail(new EmailMessage(
                                    email,
                                    "Verifica tu correo",
                                    buildVerificationHtml(link)
                            )))
                            .thenReturn(new EmailVerificationResult(VerificationStatus.SENT));
                });
    }

    public Mono<Void> verifyEmailToken(String tokenRaw) {
        String token = tokenRaw == null ? null : tokenRaw.trim();
        if (token == null || token.isBlank()) {
            return Mono.error(new IllegalArgumentException("Token requerido"));
        }
        String tokenHash = sha256(token);
        return userRepository.verifyEmailByToken(tokenHash)
                .flatMap(updated -> {
                    if (Boolean.TRUE.equals(updated)) {
                        return Mono.empty();
                    }
                    return Mono.error(new IllegalArgumentException("Token inválido o expirado"));
                });
    }

    public Mono<Void> requestRecoveryCode(String emailRaw) {
        String email = normalize(emailRaw);
        if (email == null || email.isBlank()) {
            return Mono.error(new IllegalArgumentException("Email requerido"));
        }
        return createRecoveryToken(email)
                .flatMap(token -> {
                    String link = buildRecoveryLink(token);
                    LOG.log(Level.INFO, "Sending recovery email to {0}", email);
                    return emailGateway.sendEmail(new EmailMessage(
                            email,
                            "Recupera tu cuenta",
                            buildRecoveryHtml(token, link)
                    ));
                })
                .then();
    }

    public Mono<String> createRecoveryToken(String emailRaw) {
        String email = normalize(emailRaw);
        if (email == null || email.isBlank()) {
            return Mono.error(new IllegalArgumentException("Email requerido"));
        }
        String token = generateToken();
        String tokenHash = sha256(token);
        OffsetDateTime expiresAt = OffsetDateTime.now().plus(RECOVERY_TTL);

        return userRepository.saveRecoveryCode(email, tokenHash, expiresAt)
                .flatMap(updated -> {
                    if (!Boolean.TRUE.equals(updated)) {
                        return Mono.error(new IllegalArgumentException("No fue posible guardar el codigo"));
                    }
                    return Mono.just(token);
                });
    }

    public String generateRecoveryToken() {
        return generateToken();
    }

    public Mono<Void> saveRecoveryToken(String emailRaw, String tokenRaw) {
        String email = normalize(emailRaw);
        String token = tokenRaw == null ? null : tokenRaw.trim();
        if (email == null || email.isBlank() || token == null || token.isBlank()) {
            return Mono.error(new IllegalArgumentException("Email y token requeridos"));
        }
        String tokenHash = sha256(token);
        OffsetDateTime expiresAt = OffsetDateTime.now().plus(RECOVERY_TTL);
        LOG.log(Level.INFO, "Saving recovery token email={0} hash={1} expiresAt={2}", new Object[]{email, tokenHash, expiresAt});
        return userRepository.saveRecoveryCode(email, tokenHash, expiresAt)
                .flatMap(updated -> {
                    if (!Boolean.TRUE.equals(updated)) {
                        return Mono.error(new IllegalArgumentException("No fue posible guardar el codigo"));
                    }
                    LOG.log(Level.INFO, "Recovery token stored email={0} expiresAt={1}", new Object[]{email, expiresAt});
                    return Mono.empty();
                });
    }

    public Mono<Void> confirmRecoveryCode(String tokenRaw, String newPasswordRaw) {
        String token = tokenRaw == null ? null : tokenRaw.trim();
        String newPassword = newPasswordRaw == null ? null : newPasswordRaw.trim();
        if (token == null || token.isBlank()) {
            return Mono.error(new IllegalArgumentException("Token requerido"));
        }
        if (newPassword == null || newPassword.isBlank()) {
            return Mono.error(new IllegalArgumentException("La contrasena es requerida"));
        }
        if (newPassword.length() < 8) {
            return Mono.error(new IllegalArgumentException("La contrasena debe tener al menos 8 caracteres"));
        }

        String tokenHash = sha256(token);
        LOG.log(Level.INFO, "Confirming recovery token hash={0}", tokenHash);
        return userRepository.getRecoveryStatusByTokenHash(tokenHash)
                .doOnSubscribe(sub -> LOG.log(Level.INFO, "Fetching recovery token status hash={0}", tokenHash))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Token invalido o expirado")))
                .doOnNext(status -> LOG.log(
                        Level.INFO,
                        "Recovery token fetched email={0} expiresAt={1} attempts={2}/{3}",
                        new Object[]{status.email(), status.expiresAt(), status.attempts(), status.maxAttempts()}
                ))
                .flatMap(status -> validateRecoveryToken(status)
                        .then(userRepository.updatePasswordHash(status.email(), passwordHasher.hash(newPassword)))
                        .then(totpSecretRepository.resetTotp(status.email()))
                        .then(userRepository.clearRecoveryCode(status.email()))
                );
    }

    private Mono<Void> validateRecoveryToken(RecoveryTokenStatus status) {
        LOG.log(Level.FINE, "Recovery token check for email={0} expiresAt={1} attempts={2}/{3}",
                new Object[]{
                        status.email(),
                        status.expiresAt(),
                        status.attempts(),
                        status.maxAttempts()
                });
        if (status.expiresAt() == null) {
            return Mono.error(new IllegalArgumentException("Token inválido o ya usado"));
        }
        if (status.expiresAt().isBefore(OffsetDateTime.now())) {
            return Mono.error(new IllegalArgumentException("Token expirado (expiró en " + status.expiresAt() + ")"));
        }
        if (status.attempts() != null && status.maxAttempts() != null
                && status.attempts() >= status.maxAttempts()) {
            return Mono.error(new IllegalArgumentException("Se superaron los intentos permitidos"));
        }
        return Mono.empty();
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

    private static String buildVerificationHtml(String link) {
        return """
                <p>Hola,</p>
                <p>Para verificar tu correo, abre este enlace:</p>
                <p><a href="%s">%s</a></p>
                <p>Si no solicitaste esto, ignora el mensaje.</p>
                """.formatted(link, link);
    }

    private String buildRecoveryLink(String token) {
        String frontendBase = appUrlConfig.frontendBaseUrl();
        if (frontendBase != null && !frontendBase.isBlank()) {
            String normalized = frontendBase.endsWith("/") ? frontendBase.substring(0, frontendBase.length() - 1) : frontendBase;
            return normalized + "/recover-account?token=" + token;
        }

        String backendBase = appUrlConfig.publicBaseUrl();
        if (backendBase == null || backendBase.isBlank()) {
            return "/recover-account?token=" + token;
        }
        String normalized = backendBase.endsWith("/") ? backendBase.substring(0, backendBase.length() - 1) : backendBase;
        return normalized + "/recover-account?token=" + token;
    }

    private static String buildRecoveryHtml(String token, String link) {
        return """
                <p>Hola,</p>
                <p>Link: <a href="%s">recuperar</a></p>
                <p>O pega este c\u00f3digo en la app: <strong>%s</strong></p>
                """.formatted(link, token);
    }

    private static String generateToken() {
        byte[] buf = new byte[32];
        RANDOM.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (Exception e) {
            throw new IllegalStateException("No fue posible generar hash", e);
        }
    }

    private static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    public enum VerificationStatus {
        SENT,
        ALREADY_VERIFIED
    }

    public record EmailVerificationResult(VerificationStatus status) {
    }
}
