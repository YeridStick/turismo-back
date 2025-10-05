package co.turismo.model.userIdentityPort;

import co.turismo.model.user.User;
import reactor.core.publisher.Mono;

public interface UserIdentityPort {
    Mono<UserSummary> getUserIdForEmail(String email);
    Mono<Boolean> isActiveEmail(String email);
}
