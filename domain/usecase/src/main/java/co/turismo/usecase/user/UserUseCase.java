package co.turismo.usecase.user;

import co.turismo.model.user.UpdateUserProfileRequest;
import co.turismo.model.user.User;
import co.turismo.model.user.gateways.UserRepository;
import co.turismo.model.security.gateways.PasswordHasher;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


@RequiredArgsConstructor
public class UserUseCase {
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;

    public Mono<User> createUser(User user) {
        return userRepository.save(user);
    }

    public Mono<User> updateMyProfile(String email, UpdateUserProfileRequest patch) {
        return userRepository.updateProfileByEmail(email, patch);
    }

    /** Para cuando YA hayas verificado OTP en tu flujo de autenticación. */
    public Mono<User> confirmEmailChange(Long userId, String newEmail) {
        return userRepository.updateEmailById(userId, newEmail);
    }

    public Mono<User> getUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public Mono<UserInfo> getUserInfo(String email) {
        Mono<User> userMono = userRepository.findByEmail(email);
        Mono<Boolean> verifiedMono = userRepository.isEmailVerified(email).defaultIfEmpty(false);
        Mono<Boolean> passwordEnabledMono = userRepository.isPasswordEnabled(email).defaultIfEmpty(false);
        return Mono.zip(userMono, verifiedMono, passwordEnabledMono)
                .map(t -> new UserInfo(t.getT1(), t.getT2(), t.getT3()));
    }

    public Flux<User> getAllUsers() {
        return userRepository.findAllUser()
                .switchIfEmpty(Flux.error(new Exception("No se encontraron usuarios")));
    }

    public Mono<PasswordUpdateResult> setPassword(String email, String newPassword) {
        if (newPassword == null || newPassword.isBlank()) {
            return Mono.error(new IllegalArgumentException("La contrasena es requerida"));
        }
        if (newPassword.length() < 8) {
            return Mono.error(new IllegalArgumentException("La contrasena debe tener al menos 8 caracteres"));
        }
        return userRepository.findByEmail(email)
                .then(userRepository.isPasswordEnabled(email).defaultIfEmpty(false))
                .flatMap(enabled -> {
                    String hash = passwordHasher.hash(newPassword);
                    return userRepository.updatePasswordHash(email, hash)
                            .thenReturn(enabled ? PasswordUpdateResult.UPDATED : PasswordUpdateResult.CREATED);
                });
    }

    public record UserInfo(User user, boolean emailVerified, boolean passwordEnabled) {
    }

    public enum PasswordUpdateResult {
        CREATED,
        UPDATED
    }
}
