package co.turismo.usecase.authenticate;

import co.turismo.model.authenticationsession.gateways.AuthenticationSessionRepository;
import co.turismo.model.user.gateways.UserRepository;
import co.turismo.model.verification.VerificationCode;
import co.turismo.model.verification.gateways.VerificationCodeRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@RequiredArgsConstructor
public class AuthenticateUseCase {

    private final VerificationCodeRepository emailRepository;
    private final AuthenticationSessionRepository authenticationRepository;
    private final UserRepository userRepository;
    private final SecureRandom random = new SecureRandom();

    public Mono<String> sendVerificationCode(String emailRaw) {
        final String email = normalize(emailRaw);
        return userRepository.isActiveByEmail(email)
                .filter(Boolean::booleanValue)
                .switchIfEmpty(Mono.error(new RuntimeException("Email no registrado o bloqueado")))
                .then(userRepository.resetLockIfExpired(email))    // limpia bloqueo vencido
                .then(Mono.defer(() -> {
                    String code = generateVerificationCode();
                    return authenticationRepository.storeCode(email, code)
                            .then(emailRepository.sendVerificationCode(
                                    VerificationCode.builder()
                                            .email(email)
                                            .code(code)
                                            .expirationTime(LocalDateTime.now().plusMinutes(5))
                                            .build()))
                            .thenReturn(code);
                }));
    }

    public Mono<String> authenticate(String emailRaw, String code, String ip) {
        final String email = normalize(emailRaw);

        return userRepository.isActiveByEmail(email)
                .filter(Boolean::booleanValue)
                .switchIfEmpty(Mono.error(new RuntimeException("Usuario inexistente o bloqueado")))
                .then(authenticationRepository.getStoredCode(email)
                        .switchIfEmpty(Mono.error(new RuntimeException("Código expirado"))))
                .filter(saved -> saved.equals(code))
                .switchIfEmpty(
                        userRepository.registerOtpFail(email)           // suma intento y bloquea si corresponde
                                .then(Mono.error(new RuntimeException("Código inválido")))
                )
                .flatMap(ok -> authenticationRepository.invalidateCode(email)) // OTP one-time
                .then(userRepository.registerSuccessfulLogin(email))           // marca login exitoso
                .thenMany(userRepository.findRoleNamesByEmail(email))          // roles
                .collectList()
                .map(list -> list.isEmpty() ? java.util.Set.of("VISITOR")
                        : new java.util.HashSet<>(list))
                .flatMap(roles -> authenticationRepository.generateToken(email, roles, ip));
    }

    public Mono<Boolean> validateSession(String token, String ip) {
        return authenticationRepository.validateToken(token, ip);
    }

    private String generateVerificationCode() {
        return String.format("%06d", random.nextInt(1_000_000));
    }

    private static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }
}
