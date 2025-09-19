package co.turismo.model.authenticationsession.gateways;

import reactor.core.publisher.Mono;

public interface TotpVerifier {
    Mono<Boolean> verify(String base32Secret, int code, int windowSteps);
}
