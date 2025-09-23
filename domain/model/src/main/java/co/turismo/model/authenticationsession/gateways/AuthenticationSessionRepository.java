package co.turismo.model.authenticationsession.gateways;

import reactor.core.publisher.Mono;

public interface AuthenticationSessionRepository {
    Mono<String> generateToken(String email, java.util.Set<String> roles, String ip);
    Mono<Boolean> validateToken(String token, String ip);
    Mono<String> refreshToken(String oldToken, String ip);
}
