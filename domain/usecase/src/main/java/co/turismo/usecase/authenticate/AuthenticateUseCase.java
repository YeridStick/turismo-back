package co.turismo.usecase.authenticate;

import co.turismo.model.authenticationsession.gateways.AuthenticationSessionRepository;
import co.turismo.model.user.gateways.UserRepository;
import co.turismo.model.authenticationsession.gateways.TotpSecretRepository;
import co.turismo.model.authenticationsession.gateways.TotpVerifier;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

@RequiredArgsConstructor
public class AuthenticateUseCase {

    private final AuthenticationSessionRepository authenticationRepository;
    private final UserRepository userRepository;

    private final TotpSecretRepository totpSecretRepository;
    private final TotpVerifier totpVerifier;

    // Proveedor del secreto Base32 (inyectado: p.ej. TotpSecretGenerator::generateBase32Secret)
    private final Supplier<String> secretGenerator;

    private static final String ISSUER = "TurismoApp"; // cámbialo por tu marca si quieres

    // -------------------- SETUP --------------------
    /** Genera un secreto (draft) y devuelve datos para mostrar el QR. */
    public Mono<SetupResponse> setupTotp(String emailRaw) {
        final String email = normalize(emailRaw);
        Objects.requireNonNull(email, "email");

        return userRepository.isActiveByEmail(email)
                .filter(Boolean::booleanValue)
                .switchIfEmpty(Mono.error(new RuntimeException("Usuario inexistente o bloqueado")))
                .then(totpSecretRepository.isTotpEnabledByEmail(email))
                .flatMap(enabled -> {
                    if (Boolean.TRUE.equals(enabled)) {
                        return Mono.error(new RuntimeException("TOTP ya habilitado para este usuario"));
                    }
                    final String secret = secretGenerator.get();
                    final String otpAuthUri = buildOtpAuthUri(ISSUER, email, secret);
                    return totpSecretRepository.saveSecretDraft(email, secret)
                            .thenReturn(new SetupResponse(secret, otpAuthUri));
                });
    }

    public Mono<Boolean> totpStatus(String emailRaw) {
        final String email = normalize(emailRaw);
        Objects.requireNonNull(email, "email");

        return userRepository.findByEmail(email)
                .switchIfEmpty(Mono.error(new Exception("Usuario no encontrado")))
                .flatMap(u -> {
                    boolean locked = u.getLockedUntil() != null
                            && java.time.Instant.now().isBefore(u.getLockedUntil().toInstant());
                    if (locked) {
                        return Mono.error(new Exception(
                                "Usuario bloqueado hasta " + u.getLockedUntil().toString()));
                    }
                    return totpSecretRepository.isTotpEnabledByEmail(email)
                            .defaultIfEmpty(false);
                });
    }

    // -------------------- CONFIRM --------------------
    /** Confirma el primer código y habilita TOTP. */
    public Mono<Void> confirmTotp(String emailRaw, int code) {
        final String email = normalize(emailRaw);
        Objects.requireNonNull(email, "email");

        return userRepository.isActiveByEmail(email)
                .filter(Boolean::booleanValue)
                .switchIfEmpty(Mono.error(new RuntimeException("Usuario inexistente o bloqueado")))
                .then(totpSecretRepository.getTotpSecretByEmail(email)
                        .switchIfEmpty(Mono.error(new RuntimeException("Secreto TOTP no encontrado"))))
                .flatMap(secret -> totpVerifier.verify(secret, code, 2))
                .flatMap(ok -> {
                    if (ok) return totpSecretRepository.enableTotp(email);
                    // registra intento fallido (reutilizamos la métrica de OTP fallida)
                    return userRepository.registerOtpFail(email)
                            .then(Mono.error(new RuntimeException("Código TOTP inválido")));
                });
    }

    // -------------------- LOGIN --------------------
    /** Autenticación con TOTP (ya habilitado). */
    public Mono<String> authenticateTotp(String emailRaw, int totpCode, String ip) {
        final String email = normalize(emailRaw);
        Objects.requireNonNull(email, "email");

        return userRepository.isActiveByEmail(email)
                .filter(Boolean::booleanValue)
                .switchIfEmpty(Mono.error(new RuntimeException("Usuario inexistente o bloqueado")))
                .then(totpSecretRepository.isTotpEnabledByEmail(email)
                        .switchIfEmpty(Mono.error(new RuntimeException("Usuario no encontrado"))))
                .flatMap(enabled -> {
                    if (Boolean.FALSE.equals(enabled)) {
                        return Mono.error(new RuntimeException("TOTP no habilitado"));
                    }
                    return totpSecretRepository.getTotpSecretByEmail(email);
                })
                .switchIfEmpty(Mono.error(new RuntimeException("Secreto TOTP no encontrado")))
                .flatMap(secret ->
                        totpVerifier.verify(secret, totpCode, 2)
                                .flatMap(isValid -> {
                                    if (!isValid) {
                                        return userRepository.registerOtpFail(email)
                                                .then(Mono.error(new RuntimeException("Código TOTP inválido")));
                                    }
                                    // éxito: marcar login, traer roles y emitir token
                                    return userRepository.registerSuccessfulLogin(email)
                                            .thenMany(userRepository.findRoleNamesByEmail(email))
                                            .collectList()
                                            .map(this::toRoleSetOrVisitor)
                                            .flatMap(roles -> authenticationRepository.generateToken(email, roles, ip));
                                })
                );
    }

    // -------------------- VALIDACIÓN DE SESIÓN --------------------
    public Mono<Boolean> validateSession(String token, String ip) {
        return authenticationRepository.validateToken(token, ip);
    }

    // -------------------- HELPERS --------------------
    private Set<String> toRoleSetOrVisitor(List<String> list) {
        return (list == null || list.isEmpty()) ? Set.of("VISITOR") : Set.copyOf(list);
    }

    private static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private static String url(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String buildOtpAuthUri(String issuer, String email, String base32Secret) {
        return "otpauth://totp/" + url(issuer) + ":" + url(email)
                + "?secret=" + base32Secret
                + "&issuer=" + url(issuer)
                + "&period=30&digits=6&algorithm=SHA1";
    }

    // DTO para setup
    public record SetupResponse(String secretBase32, String otpAuthUri) {}
}
