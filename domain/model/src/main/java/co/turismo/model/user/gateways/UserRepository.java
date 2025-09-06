package co.turismo.model.user.gateways;

import co.turismo.model.user.UpdateUserProfileRequest;
import co.turismo.model.user.User;
import co.turismo.model.user.UserProfileUpdate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UserRepository {
    Mono<User> findByEmail(String email);
    Flux<String> findRoleNamesByEmail(String email);
    Mono<Boolean> isActiveByEmail(String email);
    Flux<String> findRoleNameByEmail(String email);
    Mono<User> save(User user);

    // existentes
    Mono<Void> registerOtpFail(String email);
    Mono<Void> registerSuccessfulLogin(String email);
    Mono<Void> resetLockIfExpired(String email);

    // NUEVOS
    Mono<User> updateProfileByEmail(String email, UpdateUserProfileRequest patch);

    /** Útil para “confirmar” cambio de correo DESPUÉS de validar OTP por otro flujo. */
    Mono<User> updateEmailById(Long userId, String newEmail);
}