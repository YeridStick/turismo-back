package co.turismo.model.payment.gateways;

import co.turismo.model.payment.PaymentTransaction;
import co.turismo.model.payment.WompiCheckoutData;
import co.turismo.model.payment.WompiEventData;
import reactor.core.publisher.Mono;

public interface WompiGateway {
    boolean isEnabled();
    long checkoutExpirationMinutes();
    WompiCheckoutData buildCheckout(PaymentTransaction transaction, String customerEmail);
    Mono<WompiEventData> parseAndValidateEvent(String rawPayload, String eventChecksum);
}
