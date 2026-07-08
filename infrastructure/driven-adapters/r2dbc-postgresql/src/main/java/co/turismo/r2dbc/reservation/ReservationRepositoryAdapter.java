package co.turismo.r2dbc.reservation;

import co.turismo.model.reservation.ReservationDraft;
import co.turismo.model.reservation.gateways.ReservationGateway;
import io.r2dbc.spi.Row;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Repository
@RequiredArgsConstructor
public class ReservationRepositoryAdapter implements ReservationGateway {

    private static final Logger LOG = LoggerFactory.getLogger(ReservationRepositoryAdapter.class);

    private static final String RESERVATION_SELECT = """
            SELECT
                r.id,
                r.user_email,
                r.tour_package_id,
                r.agency_id,
                r.package_title,
                r.total_amount,
                r.currency,
                r.start_date,
                r.end_date,
                r.status,
                r.created_at,
                r.updated_at,
                d.travelers,
                d.customer_phone,
                d.contact_preference,
                d.customer_message,
                d.consent_accepted,
                d.consent_version,
                d.consent_accepted_at,
                d.payment_provider,
                d.payment_status,
                d.payment_id,
                d.paid_at,
                d.agency_notes,
                d.contacted_at,
                d.confirmed_at,
                d.cancelled_at
            FROM reservations r
            LEFT JOIN reservation_details d ON d.reservation_id = r.id
            """;

    private final DatabaseClient db;

    @Override
    public Mono<ReservationDraft> createPendingReservation(ReservationDraft reservation) {
        String sql = """
            WITH inserted AS (
                INSERT INTO reservations (
                    id,
                    user_email,
                    tour_package_id,
                    agency_id,
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
                    :agencyId,
                    :packageTitle,
                    :totalAmount,
                    :currency,
                    :startDate,
                    :endDate,
                    :status,
                    NOW(),
                    NOW()
                )
                RETURNING id
            )
            INSERT INTO reservation_details (
                reservation_id,
                travelers,
                customer_phone,
                contact_preference,
                customer_message,
                consent_accepted,
                consent_version,
                consent_accepted_at,
                payment_provider,
                payment_status,
                payment_id,
                paid_at,
                created_at,
                updated_at
            )
            SELECT
                id,
                :travelers,
                :customerPhone,
                :contactPreference,
                :customerMessage,
                :consentAccepted,
                :consentVersion,
                :consentAcceptedAt,
                :paymentProvider,
                :paymentStatus,
                :paymentId,
                :paidAt,
                NOW(),
                NOW()
            FROM inserted
            """;

        return bindReservation(db.sql(sql), reservation)
                .fetch()
                .rowsUpdated()
                .doOnNext(rows -> LOG.info(
                        "Reserva solicitada creada. reservationId={} tourPackageId={} agencyId={} status={} rowsUpdated={}",
                        reservation.getId(),
                        reservation.getTourPackageId(),
                        reservation.getAgencyId(),
                        reservation.getStatus(),
                        rows))
                .thenReturn(reservation);
    }

    @Override
    public Mono<ReservationDraft> updateUserReservationWithinGrace(
            String reservationId,
            String userEmail,
            ReservationDraft reservation,
            OffsetDateTime editableUntil
    ) {
        String sql = """
            WITH updated AS (
                UPDATE reservations
                SET start_date = :startDate,
                    end_date = :endDate,
                    updated_at = NOW()
                WHERE id = :reservationId
                  AND user_email = :userEmail
                  AND status = 'requested'
                  AND created_at <= :editableUntil
                  AND NOW() <= :editableUntil
                RETURNING id
            )
            UPDATE reservation_details
            SET travelers = :travelers,
                customer_phone = :customerPhone,
                contact_preference = :contactPreference,
                customer_message = :customerMessage,
                updated_at = NOW()
            WHERE reservation_id IN (SELECT id FROM updated)
            """;

        DatabaseClient.GenericExecuteSpec spec = db.sql(sql)
                .bind("reservationId", reservationId)
                .bind("userEmail", userEmail)
                .bind("startDate", reservation.getStartDate())
                .bind("endDate", reservation.getEndDate())
                .bind("travelers", reservation.getTravelers())
                .bind("contactPreference", reservation.getContactPreference())
                .bind("editableUntil", editableUntil);

        spec = bindNullable(spec, "customerPhone", reservation.getCustomerPhone(), String.class);
        spec = bindNullable(spec, "customerMessage", reservation.getCustomerMessage(), String.class);

        return spec.fetch()
                .rowsUpdated()
                .filter(rows -> rows > 0)
                .flatMap(rows -> findByIdForUser(reservationId, userEmail));
    }

    @Override
    public Mono<Boolean> deleteUserReservationWithinGrace(String reservationId, String userEmail, OffsetDateTime editableUntil) {
        return db.sql("""
                    DELETE FROM reservations
                    WHERE id = :reservationId
                      AND user_email = :userEmail
                      AND status = 'requested'
                      AND created_at <= :editableUntil
                      AND NOW() <= :editableUntil
                """)
                .bind("reservationId", reservationId)
                .bind("userEmail", userEmail)
                .bind("editableUntil", editableUntil)
                .fetch()
                .rowsUpdated()
                .map(rows -> rows > 0);
    }

    @Override
    public Flux<ReservationDraft> findByUserEmail(String userEmail, int limit, int offset) {
        return db.sql(RESERVATION_SELECT + """
                    WHERE r.user_email = :userEmail
                    ORDER BY r.created_at DESC
                    LIMIT :limit OFFSET :offset
                """)
                .bind("userEmail", userEmail)
                .bind("limit", limit)
                .bind("offset", offset)
                .map((row, metadata) -> toReservation(row))
                .all();
    }

    @Override
    public Mono<ReservationDraft> findByIdForUser(String reservationId, String userEmail) {
        return db.sql(RESERVATION_SELECT + """
                    WHERE r.id = :reservationId
                      AND r.user_email = :userEmail
                """)
                .bind("reservationId", reservationId)
                .bind("userEmail", userEmail)
                .map((row, metadata) -> toReservation(row))
                .one();
    }

    @Override
    public Flux<ReservationDraft> findAllForAdmin(String status, int limit, int offset) {
        String sql = RESERVATION_SELECT + """
                    WHERE r.created_at <= NOW() - INTERVAL '2 minutes'
                      AND (:status IS NULL OR r.status = :status)
                    ORDER BY r.created_at DESC
                    LIMIT :limit OFFSET :offset
                """;

        return bindNullable(db.sql(sql), "status", status, String.class)
                .bind("limit", limit)
                .bind("offset", offset)
                .map((row, metadata) -> toReservation(row))
                .all();
    }

    @Override
    public Mono<ReservationDraft> findByIdForAdmin(String reservationId) {
        return db.sql(RESERVATION_SELECT + """
                    WHERE r.id = :reservationId
                      AND r.created_at <= NOW() - INTERVAL '2 minutes'
                """)
                .bind("reservationId", reservationId)
                .map((row, metadata) -> toReservation(row))
                .one();
    }

    @Override
    public Flux<ReservationDraft> findByAgencyId(Long agencyId, String status, int limit, int offset) {
        String sql = RESERVATION_SELECT + """
                    WHERE r.agency_id = :agencyId
                      AND r.created_at <= NOW() - INTERVAL '2 minutes'
                      AND (:status IS NULL OR r.status = :status)
                    ORDER BY r.created_at DESC
                    LIMIT :limit OFFSET :offset
                """;

        return bindNullable(db.sql(sql).bind("agencyId", agencyId), "status", status, String.class)
                .bind("limit", limit)
                .bind("offset", offset)
                .map((row, metadata) -> toReservation(row))
                .all();
    }

    @Override
    public Mono<ReservationDraft> findByIdForAgency(String reservationId, Long agencyId) {
        return db.sql(RESERVATION_SELECT + """
                    WHERE r.id = :reservationId
                      AND r.agency_id = :agencyId
                      AND r.created_at <= NOW() - INTERVAL '2 minutes'
                """)
                .bind("reservationId", reservationId)
                .bind("agencyId", agencyId)
                .map((row, metadata) -> toReservation(row))
                .one();
    }

    @Override
    public Mono<ReservationDraft> markContactedByAgencyReply(String reservationId, Long agencyId) {
        String sql = """
            WITH updated AS (
                UPDATE reservations
                SET status = 'contacted',
                    updated_at = NOW()
                WHERE id = :reservationId
                  AND agency_id = :agencyId
                  AND status = 'requested'
                RETURNING id
            )
            INSERT INTO reservation_details (
                reservation_id,
                payment_status,
                contacted_at,
                created_at,
                updated_at
            )
            SELECT
                id,
                'pending',
                NOW(),
                NOW(),
                NOW()
            FROM updated
            ON CONFLICT (reservation_id) DO UPDATE
            SET payment_status = COALESCE(reservation_details.payment_status, EXCLUDED.payment_status),
                contacted_at = COALESCE(reservation_details.contacted_at, EXCLUDED.contacted_at),
                updated_at = NOW()
            """;

        return db.sql(sql)
                .bind("reservationId", reservationId)
                .bind("agencyId", agencyId)
                .fetch()
                .rowsUpdated()
                .doOnNext(rows -> LOG.info(
                        "Reserva marcada como contactada por respuesta de agencia. reservationId={} agencyId={} rowsUpdated={}",
                        reservationId,
                        agencyId,
                        rows))
                .filter(rows -> rows > 0)
                .flatMap(rows -> findByIdForAgency(reservationId, agencyId));
    }

    @Override
    public Mono<ReservationDraft> updateAgencyStatus(
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
    ) {
        String sql = """
            WITH updated AS (
                UPDATE reservations
                SET status = :status,
                    updated_at = NOW()
                WHERE id = :reservationId
                  AND agency_id = :agencyId
                RETURNING id
            )
            INSERT INTO reservation_details (
                reservation_id,
                agency_notes,
                payment_provider,
                payment_status,
                paid_at,
                contacted_at,
                confirmed_at,
                cancelled_at,
                created_at,
                updated_at
            )
            SELECT
                id,
                :agencyNotes,
                :paymentProvider,
                :paymentStatus,
                :paidAt,
                :contactedAt,
                :confirmedAt,
                :cancelledAt,
                NOW(),
                NOW()
            FROM updated
            ON CONFLICT (reservation_id) DO UPDATE
            SET agency_notes = COALESCE(EXCLUDED.agency_notes, reservation_details.agency_notes),
                payment_provider = COALESCE(EXCLUDED.payment_provider, reservation_details.payment_provider),
                payment_status = COALESCE(EXCLUDED.payment_status, reservation_details.payment_status),
                paid_at = COALESCE(EXCLUDED.paid_at, reservation_details.paid_at),
                contacted_at = COALESCE(EXCLUDED.contacted_at, reservation_details.contacted_at),
                confirmed_at = COALESCE(EXCLUDED.confirmed_at, reservation_details.confirmed_at),
                cancelled_at = COALESCE(EXCLUDED.cancelled_at, reservation_details.cancelled_at),
                updated_at = NOW()
            """;

        DatabaseClient.GenericExecuteSpec spec = db.sql(sql)
                .bind("reservationId", reservationId)
                .bind("agencyId", agencyId)
                .bind("status", status);

        spec = bindNullable(spec, "agencyNotes", agencyNotes, String.class);
        spec = bindNullable(spec, "paymentProvider", paymentProvider, String.class);
        spec = bindNullable(spec, "paymentStatus", paymentStatus, String.class);
        spec = bindNullable(spec, "paidAt", paidAt, OffsetDateTime.class);
        spec = bindNullable(spec, "contactedAt", contactedAt, OffsetDateTime.class);
        spec = bindNullable(spec, "confirmedAt", confirmedAt, OffsetDateTime.class);
        spec = bindNullable(spec, "cancelledAt", cancelledAt, OffsetDateTime.class);

        return spec.fetch()
                .rowsUpdated()
                .doOnNext(rows -> LOG.info(
                        "Estado de reserva actualizado. reservationId={} agencyId={} status={} rowsUpdated={}",
                        reservationId,
                        agencyId,
                        status,
                        rows))
                .filter(rows -> rows > 0)
                .flatMap(rows -> findByIdForAgency(reservationId, agencyId));
    }

    @Override
    public Mono<ReservationDraft> markWompiCheckoutCreated(String reservationId, String userEmail) {
        String sql = """
            UPDATE reservation_details
            SET payment_provider = 'wompi',
                payment_status = 'checkout_created',
                payment_id = NULL,
                updated_at = NOW()
            WHERE reservation_id = :reservationId
              AND EXISTS (
                  SELECT 1
                  FROM reservations r
                  WHERE r.id = reservation_details.reservation_id
                    AND r.user_email = :userEmail
                    AND r.status = 'awaiting_payment'
              )
            """;

        return db.sql(sql)
                .bind("reservationId", reservationId)
                .bind("userEmail", userEmail)
                .fetch()
                .rowsUpdated()
                .filter(rows -> rows > 0)
                .flatMap(rows -> findByIdForUser(reservationId, userEmail));
    }

    @Override
    public Mono<ReservationDraft> applyWompiPaymentResult(
            String reservationId,
            String paymentStatus,
            String paymentId,
            OffsetDateTime paidAt,
            OffsetDateTime confirmedAt,
            boolean confirmReservation
    ) {
        String sql = """
            WITH updated_reservation AS (
                UPDATE reservations
                SET status = CASE
                        WHEN :confirmReservation AND status = 'awaiting_payment' THEN 'confirmed'
                        ELSE status
                    END,
                    updated_at = NOW()
                WHERE id = :reservationId
                  AND status IN ('awaiting_payment', 'confirmed')
                RETURNING id
            )
            UPDATE reservation_details
            SET payment_provider = 'wompi',
                payment_status = :paymentStatus,
                payment_id = COALESCE(:paymentId, payment_id),
                paid_at = COALESCE(:paidAt, paid_at),
                confirmed_at = COALESCE(:confirmedAt, confirmed_at),
                updated_at = NOW()
            WHERE reservation_id IN (SELECT id FROM updated_reservation)
            """;

        DatabaseClient.GenericExecuteSpec spec = db.sql(sql)
                .bind("reservationId", reservationId)
                .bind("paymentStatus", paymentStatus)
                .bind("confirmReservation", confirmReservation);
        spec = bindNullable(spec, "paymentId", paymentId, String.class);
        spec = bindNullable(spec, "paidAt", paidAt, OffsetDateTime.class);
        spec = bindNullable(spec, "confirmedAt", confirmedAt, OffsetDateTime.class);

        return spec.fetch()
                .rowsUpdated()
                .filter(rows -> rows > 0)
                .flatMap(rows -> findByIdForAdmin(reservationId));
    }

    private DatabaseClient.GenericExecuteSpec bindReservation(
            DatabaseClient.GenericExecuteSpec spec,
            ReservationDraft reservation
    ) {
        spec = spec.bind("id", reservation.getId())
                .bind("userEmail", reservation.getUserEmail())
                .bind("tourPackageId", reservation.getTourPackageId())
                .bind("agencyId", reservation.getAgencyId())
                .bind("packageTitle", reservation.getPackageTitle())
                .bind("totalAmount", reservation.getTotalAmount())
                .bind("currency", reservation.getCurrency())
                .bind("startDate", reservation.getStartDate())
                .bind("endDate", reservation.getEndDate())
                .bind("status", reservation.getStatus())
                .bind("travelers", reservation.getTravelers())
                .bind("consentAccepted", reservation.getConsentAccepted())
                .bind("consentVersion", reservation.getConsentVersion())
                .bind("consentAcceptedAt", reservation.getConsentAcceptedAt())
                .bind("paymentProvider", reservation.getPaymentProvider())
                .bind("paymentStatus", reservation.getPaymentStatus());

        spec = bindNullable(spec, "customerPhone", reservation.getCustomerPhone(), String.class);
        spec = bindNullable(spec, "contactPreference", reservation.getContactPreference(), String.class);
        spec = bindNullable(spec, "customerMessage", reservation.getCustomerMessage(), String.class);
        spec = bindNullable(spec, "paymentId", reservation.getPaymentId(), String.class);
        spec = bindNullable(spec, "paidAt", reservation.getPaidAt(), OffsetDateTime.class);
        return spec;
    }

    private ReservationDraft toReservation(Row row) {
        return ReservationDraft.builder()
                .id(row.get("id", String.class))
                .userEmail(row.get("user_email", String.class))
                .tourPackageId(row.get("tour_package_id", Long.class))
                .agencyId(row.get("agency_id", Long.class))
                .packageTitle(row.get("package_title", String.class))
                .totalAmount(row.get("total_amount", BigDecimal.class))
                .currency(row.get("currency", String.class))
                .startDate(row.get("start_date", LocalDate.class))
                .endDate(row.get("end_date", LocalDate.class))
                .travelers(row.get("travelers", Integer.class))
                .customerPhone(row.get("customer_phone", String.class))
                .contactPreference(row.get("contact_preference", String.class))
                .customerMessage(row.get("customer_message", String.class))
                .consentAccepted(row.get("consent_accepted", Boolean.class))
                .consentVersion(row.get("consent_version", String.class))
                .consentAcceptedAt(row.get("consent_accepted_at", OffsetDateTime.class))
                .status(row.get("status", String.class))
                .paymentProvider(row.get("payment_provider", String.class))
                .paymentStatus(row.get("payment_status", String.class))
                .paymentId(row.get("payment_id", String.class))
                .paidAt(row.get("paid_at", OffsetDateTime.class))
                .agencyNotes(row.get("agency_notes", String.class))
                .contactedAt(row.get("contacted_at", OffsetDateTime.class))
                .confirmedAt(row.get("confirmed_at", OffsetDateTime.class))
                .cancelledAt(row.get("cancelled_at", OffsetDateTime.class))
                .createdAt(row.get("created_at", OffsetDateTime.class))
                .updatedAt(row.get("updated_at", OffsetDateTime.class))
                .build();
    }

    private static <T> DatabaseClient.GenericExecuteSpec bindNullable(
            DatabaseClient.GenericExecuteSpec spec,
            String name,
            T value,
            Class<?> type
    ) {
        return value == null
                ? spec.bindNull(name, type)
                : spec.bind(name, value);
    }
}
