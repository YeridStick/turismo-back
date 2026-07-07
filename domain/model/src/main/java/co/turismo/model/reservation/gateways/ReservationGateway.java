package co.turismo.model.reservation.gateways;

import co.turismo.model.reservation.ReservationDraft;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;

public interface ReservationGateway {
    Mono<ReservationDraft> createPendingReservation(ReservationDraft reservation);
    Mono<ReservationDraft> updateUserReservationWithinGrace(String reservationId, String userEmail, ReservationDraft reservation, OffsetDateTime editableUntil);
    Mono<Boolean> deleteUserReservationWithinGrace(String reservationId, String userEmail, OffsetDateTime editableUntil);
    Flux<ReservationDraft> findByUserEmail(String userEmail, int limit, int offset);
    Mono<ReservationDraft> findByIdForUser(String reservationId, String userEmail);
    Flux<ReservationDraft> findAllForAdmin(String status, int limit, int offset);
    Mono<ReservationDraft> findByIdForAdmin(String reservationId);
    Flux<ReservationDraft> findByAgencyId(Long agencyId, String status, int limit, int offset);
    Mono<ReservationDraft> findByIdForAgency(String reservationId, Long agencyId);
    Mono<ReservationDraft> markContactedByAgencyReply(String reservationId, Long agencyId);
    Mono<ReservationDraft> updateAgencyStatus(
            String reservationId,
            Long agencyId,
            String status,
            String agencyNotes,
            String paymentProvider,
            String paymentStatus,
            OffsetDateTime paidAt,
            OffsetDateTime contactedAt,
            OffsetDateTime confirmedAt,
            OffsetDateTime cancelledAt
    );
}
