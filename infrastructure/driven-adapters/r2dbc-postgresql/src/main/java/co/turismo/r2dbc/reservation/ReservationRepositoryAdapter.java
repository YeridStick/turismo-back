package co.turismo.r2dbc.reservation;

import co.turismo.model.reservation.ReservationDraft;
import co.turismo.model.reservation.gateways.ReservationGateway;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class ReservationRepositoryAdapter implements ReservationGateway {

    private static final Logger LOG = LoggerFactory.getLogger(ReservationRepositoryAdapter.class);

    private final DatabaseClient db;

    @Override
    public Mono<ReservationDraft> createPendingReservation(ReservationDraft reservation) {
        String sql = """
            INSERT INTO reservations (
                id,
                user_email,
                tour_package_id,
                package_title,
                total_amount,
                currency,
                start_date,
                end_date,
                status,
                created_at,
                updated_at
            )
            VALUES (
                :id,
                :userEmail,
                :tourPackageId,
                :packageTitle,
                :totalAmount,
                :currency,
                :startDate,
                :endDate,
                :status,
                NOW(),
                NOW()
            )
            """;

        return db.sql(sql)
                .bind("id", reservation.getId())
                .bind("userEmail", reservation.getUserEmail())
                .bind("tourPackageId", reservation.getTourPackageId())
                .bind("packageTitle", reservation.getPackageTitle())
                .bind("totalAmount", reservation.getTotalAmount())
                .bind("currency", reservation.getCurrency())
                .bind("startDate", reservation.getStartDate())
                .bind("endDate", reservation.getEndDate())
                .bind("status", reservation.getStatus())
                .fetch()
                .rowsUpdated()
                .doOnNext(rows -> LOG.info(
                        "Reserva pendiente creada. reservationId={} tourPackageId={} startDate={} endDate={} rowsUpdated={}",
                        reservation.getId(),
                        reservation.getTourPackageId(),
                        reservation.getStartDate(),
                        reservation.getEndDate(),
                        rows))
                .thenReturn(reservation);
    }
}
