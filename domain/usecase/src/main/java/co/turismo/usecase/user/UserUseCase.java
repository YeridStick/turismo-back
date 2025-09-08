package co.turismo.usecase.user;

import co.turismo.model.user.UpdateUserProfileRequest;
import co.turismo.model.user.User;
import co.turismo.model.user.gateways.UserRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class UserUseCase {
    private final UserRepository userRepository;

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

}