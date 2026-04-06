package co.turismo.model.user.gateways;

import co.turismo.model.user.UpdateUserProfileRequest;
import co.turismo.model.user.User;
import co.turismo.model.user.RecoveryStatus;
import co.turismo.model.user.RecoveryTokenStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UserRepository {
    Mono<User> findByEmail(String email);
    Flux<String> findRoleNamesByEmail(String email);
    Mono<Boolean> isActiveByEmail(String email);
    Flux<String> findRoleNameByEmail(String email);
    Mono<User> save(User user);
    Flux<User> findByAgencyId(Long agencyId);

    // existentes
    Mono<Void> registerOtpFail(String email);
    Mono<Void> registerSuccessfulLogin(String email);
    Mono<Void> resetLockIfExpired(String email);

    // NUEVOS
    Mono<User> updateProfileByEmail(String email, UpdateUserProfileRequest patch);

    /** Útil para “confirmar” cambio de correo DESPUÉS de validar OTP por otro flujo. */
    Mono<User> updateEmailById(Long userId, String newEmail);
    Flux<User> findAllUser();

    // Seguridad / verificación de correo
    Mono<Boolean> isEmailVerified(String email);
    Mono<Void> saveEmailVerificationToken(String email, String tokenHash, java.time.OffsetDateTime expiresAt);
    Mono<Boolean> verifyEmailByToken(String tokenHash);

    // Recuperación
    Mono<Boolean> saveRecoveryCode(String email, String codeHash, java.time.OffsetDateTime expiresAt);
    Mono<RecoveryStatus> getRecoveryStatus(String email);
    Mono<RecoveryTokenStatus> getRecoveryStatusByTokenHash(String tokenHash);
    Mono<Void> incrementRecoveryAttempts(String email);
    Mono<Void> clearRecoveryCode(String email);

    // Contraseña
    Mono<String> getPasswordHash(String email);
    Mono<Void> updatePasswordHash(String email, String passwordHash);
}
