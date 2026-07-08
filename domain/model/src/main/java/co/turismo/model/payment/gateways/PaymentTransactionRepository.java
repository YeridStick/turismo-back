package co.turismo.model.payment.gateways;

import co.turismo.model.payment.PaymentTransaction;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;

public interface PaymentTransactionRepository {
    Mono<PaymentTransaction> save(PaymentTransaction transaction);
    Mono<PaymentTransaction> findLatestByReservationId(String reservationId);
    Mono<PaymentTransaction> findReusableWompiTransaction(String reservationId, OffsetDateTime now);
    Mono<PaymentTransaction> findByReference(String reference);
    Mono<Boolean> existsBlockingWompiTransaction(String reservationId, OffsetDateTime now);
    Mono<Boolean> existsPaidWompiTransaction(String reservationId);
    Mono<PaymentTransaction> updateProviderResult(
            String reference,
            String providerTransactionId,
            String providerStatus,
            String status,
            String responsePayload,
            OffsetDateTime paidAt
    );
}
