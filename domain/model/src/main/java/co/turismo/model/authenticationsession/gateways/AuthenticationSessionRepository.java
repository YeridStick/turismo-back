package co.turismo.model.authenticationsession.gateways;

import reactor.core.publisher.Mono;

public interface AuthenticationSessionRepository {
    Mono<String> generateToken(String email, java.util.Set<String> roles, String ip);
    Mono<Boolean> validateToken(String token, String ip);
    Mono<Void> storeCode(String email, String code);
    Mono<String> getStoredCode(String email);
    Mono<Void> invalidateCode(String email);
    String getAdminEmail();
}
