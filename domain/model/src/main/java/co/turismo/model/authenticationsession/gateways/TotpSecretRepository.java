package co.turismo.model.authenticationsession.gateways;

import reactor.core.publisher.Mono;

public interface TotpSecretRepository {
    Mono<String> getTotpSecretByEmail(String email);
    Mono<Boolean> isTotpEnabledByEmail(String email);
    Mono<Void> saveSecretDraft(String email, String base32Secret);
    Mono<Void> enableTotp(String email);
}