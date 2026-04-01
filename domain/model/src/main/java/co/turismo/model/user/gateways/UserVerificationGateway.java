package co.turismo.model.user.gateways;
 
import co.turismo.model.user.EmailVerificationResult;
import reactor.core.publisher.Mono;
 
public interface UserVerificationGateway {
    /**
     * Genera un token, lo guarda en la base de datos y envía el correo de verificación.
     * @param email Correo del usuario a verificar.
     * @return Mono con el resultado del envío (SENT o ALREADY_VERIFIED).
     */
    Mono<EmailVerificationResult> sendVerificationEmail(String email);
}
