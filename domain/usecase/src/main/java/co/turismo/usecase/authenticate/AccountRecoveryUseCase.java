package co.turismo.usecase.authenticate;

import co.turismo.model.authenticationsession.gateways.TotpSecretRepository;
import co.turismo.model.common.AppUrlConfig;
import co.turismo.model.notification.EmailMessage;
import co.turismo.model.notification.gateways.EmailGateway;
import co.turismo.model.user.RecoveryStatus;
import co.turismo.model.user.gateways.UserRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;

@RequiredArgsConstructor
public class AccountRecoveryUseCase {

    private static final Duration VERIFICATION_TTL = Duration.ofHours(24);
    private static final Duration RECOVERY_TTL = Duration.ofMinutes(10);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final TotpSecretRepository totpSecretRepository;
    private final EmailGateway emailGateway;
    private final AppUrlConfig appUrlConfig;

    public Mono<Void> sendVerificationEmail(String emailRaw) {
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
                        return Mono.empty();
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
                            )));
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

        return userRepository.isActiveByEmail(email)
                .filter(Boolean::booleanValue)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Usuario inexistente o bloqueado")))
                .then(userRepository.isEmailVerified(email))
                .flatMap(verified -> {
                    if (!Boolean.TRUE.equals(verified)) {
                        return Mono.error(new IllegalArgumentException("Correo no verificado"));
                    }
                    String code = generateNumericCode();
                    String codeHash = sha256(code);
                    OffsetDateTime expiresAt = OffsetDateTime.now().plus(RECOVERY_TTL);

                    return userRepository.saveRecoveryCode(email, codeHash, expiresAt)
                            .then(emailGateway.sendEmail(new EmailMessage(
                                    email,
                                    "Codigo de recuperacion",
                                    buildRecoveryHtml(code)
                            )));
                });
    }

    public Mono<Void> confirmRecoveryCode(String emailRaw, String codeRaw) {
        String email = normalize(emailRaw);
        String code = codeRaw == null ? null : codeRaw.trim();
        if (email == null || email.isBlank() || code == null || code.isBlank()) {
            return Mono.error(new IllegalArgumentException("Email y codigo requeridos"));
        }

        return userRepository.getRecoveryStatus(email)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("No hay solicitud de recuperación")))
                .flatMap(status -> validateRecoveryCode(email, code, status))
                .then(totpSecretRepository.resetTotp(email));
    }

    private Mono<Void> validateRecoveryCode(String email, String code, RecoveryStatus status) {
        if (status.expiresAt() == null || status.codeHash() == null) {
            return Mono.error(new IllegalArgumentException("Codigo no solicitado"));
        }
        if (status.expiresAt().isBefore(OffsetDateTime.now())) {
            return Mono.error(new IllegalArgumentException("Codigo expirado"));
        }
        if (status.attempts() != null && status.maxAttempts() != null
                && status.attempts() >= status.maxAttempts()) {
            return Mono.error(new IllegalArgumentException("Se superaron los intentos permitidos"));
        }

        String codeHash = sha256(code);
        if (!codeHash.equals(status.codeHash())) {
            return userRepository.incrementRecoveryAttempts(email)
                    .then(Mono.error(new IllegalArgumentException("Codigo invalido")));
        }

        return userRepository.clearRecoveryCode(email);
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

    private static String buildRecoveryHtml(String code) {
        return """
                <p>Tu codigo de recuperacion es:</p>
                <h2>%s</h2>
                <p>Este codigo expira en 10 minutos.</p>
                """.formatted(code);
    }

    private static String generateToken() {
        byte[] buf = new byte[32];
        RANDOM.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    private static String generateNumericCode() {
        int value = 100000 + RANDOM.nextInt(900000);
        return Integer.toString(value);
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
}
