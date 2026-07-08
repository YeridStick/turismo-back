package co.turismo.model.payment.gateways;

import co.turismo.model.payment.PaymentEvent;
import reactor.core.publisher.Mono;

public interface PaymentEventRepository {
    Mono<PaymentEvent> saveIfAbsent(PaymentEvent event);
    Mono<Boolean> markProcessed(Long id);
    Mono<Boolean> markFailed(Long id, String error);
}
