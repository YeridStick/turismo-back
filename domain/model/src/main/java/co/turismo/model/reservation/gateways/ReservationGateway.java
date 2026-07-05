package co.turismo.model.reservation.gateways;

import co.turismo.model.reservation.ReservationDraft;
import reactor.core.publisher.Mono;

public interface ReservationGateway {
    Mono<ReservationDraft> createPendingReservation(ReservationDraft reservation);
}
