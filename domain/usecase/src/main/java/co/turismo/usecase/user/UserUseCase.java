package co.turismo.usecase.user;

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
}
