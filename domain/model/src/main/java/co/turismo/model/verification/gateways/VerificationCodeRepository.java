package co.turismo.model.verification.gateways;

import co.turismo.model.verification.VerificationCode;
import reactor.core.publisher.Mono;

public interface VerificationCodeRepository {
    Mono<Void> sendVerificationCode(VerificationCode verificationCode);
}
