package co.turismo.model.payment.gateways;

import co.turismo.model.payment.PaymentReservation;
import reactor.core.publisher.Mono;

public interface PaymentReservationGateway {
    Mono<PaymentReservation> createPendingReservation(PaymentReservation reservation);
    Mono<Void> markReservationAsPaid(String reservationId, String paymentId);
}
