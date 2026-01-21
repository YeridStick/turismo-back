package co.turismo.model.notification.gateways;

import co.turismo.model.notification.EmailMessage;
import reactor.core.publisher.Mono;

public interface EmailGateway {
    Mono<Void> sendEmail(EmailMessage message);
}
