package co.turismo.model.reservation.gateways;

import co.turismo.model.reservation.ReservationMessage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ReservationMessageGateway {
    Mono<ReservationMessage> save(ReservationMessage message);
    Flux<ReservationMessage> findByReservationId(String reservationId, int limit, int offset);
}
