package co.turismo.r2dbc.userIdentityPortRepository;

import co.turismo.model.user.gateways.UserRepository;
import co.turismo.model.userIdentityPort.UserIdentityPort;
import co.turismo.model.userIdentityPort.UserSummary;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public class UserIdentityPortAdapter implements UserIdentityPort {
    private final UserRepository userRepository;

    public UserIdentityPortAdapter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }


    @Override
    public Mono<UserSummary> getUserIdForEmail(String email) {
        return userRepository.findByEmail(normalize(email))
                .map(u -> new UserSummary(u.getId(), u.getEmail()));
    }

    @Override
    public Mono<Boolean> isActiveEmail(String email) {
        return userRepository.isActiveByEmail(normalize(email))
                .defaultIfEmpty(false);
    }

    private static String normalize(String e) {
        return e == null ? null : e.trim().toLowerCase();
    }
}
