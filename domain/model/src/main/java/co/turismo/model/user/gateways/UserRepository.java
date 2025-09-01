package co.turismo.model.user.gateways;

import co.turismo.model.user.User;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UserRepository {
    Mono<User> findByEmail(String email);
    Flux<String> findRoleNamesByEmail(String email);
    Mono<Boolean> isActiveByEmail(String email);
    Flux<String> findRoleNameByEmail(String email);
    Mono<User> save(User user);
    Mono<Void> registerOtpFail(String email);
    Mono<Void> registerSuccessfulLogin(String email);
    Mono<Void> resetLockIfExpired(String email);
}
