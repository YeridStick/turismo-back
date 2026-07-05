package co.turismo.r2dbc.payment;

import co.turismo.model.payment.PaymentReservation;
import co.turismo.model.payment.gateways.PaymentReservationGateway;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class ReservationPaymentRepositoryAdapter implements PaymentReservationGateway {

    private static final Logger LOG = LoggerFactory.getLogger(ReservationPaymentRepositoryAdapter.class);

    private final DatabaseClient db;

    @Override
    public Mono<PaymentReservation> createPendingReservation(PaymentReservation reservation) {
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

    @Override
    public Mono<Void> markReservationAsPaid(String reservationId, String paymentId) {
        String sql = """
            UPDATE reservations
            SET status = 'paid',
                payment_id = :paymentId,
                paid_at = NOW(),
                updated_at = NOW()
            WHERE id = :reservationId
            """;

        DatabaseClient.GenericExecuteSpec spec = db.sql(sql)
                .bind("reservationId", reservationId);

        spec = paymentId == null
                ? spec.bindNull("paymentId", String.class)
                : spec.bind("paymentId", paymentId);

        return spec
                .fetch()
                .rowsUpdated()
                .doOnNext(rows -> {
                    if (rows == 0) {
                        LOG.warn("No se actualizó ninguna reserva pagada. reservationId={} paymentId={}", reservationId, paymentId);
                    } else {
                        LOG.info("Reserva marcada como pagada. reservationId={} paymentId={} rowsUpdated={}",
                                reservationId,
                                paymentId,
                                rows);
                    }
                })
                .then();
    }
}
