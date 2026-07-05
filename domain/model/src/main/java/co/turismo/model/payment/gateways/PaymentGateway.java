package co.turismo.model.payment.gateways;

import co.turismo.model.payment.PaymentCheckoutResult;
import co.turismo.model.payment.PaymentOrder;
import co.turismo.model.payment.PaymentStatusResult;
import reactor.core.publisher.Mono;

public interface PaymentGateway {
    Mono<PaymentCheckoutResult> createPreference(PaymentOrder order);
    Mono<PaymentStatusResult> getPaymentStatus(String paymentId);
    Mono<PaymentStatusResult> getMerchantOrderPaymentStatus(String merchantOrderId);
}
