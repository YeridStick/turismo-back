package co.turismo.api.dto.reservation;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Schema(name = "ReservationResponse")
public record ReservationResponse(
        String id,
        Long tourPackageId,
        Long agencyId,
        String packageTitle,
        BigDecimal totalAmount,
        String currency,
        LocalDate startDate,
        LocalDate endDate,
        Integer travelers,
        String customerEmail,
        String customerPhone,
        String contactPreference,
        String message,
        Boolean consentAccepted,
        String consentVersion,
        String status,
        String paymentProvider,
        String paymentStatus,
        String paymentId,
        OffsetDateTime paidAt,
        String agencyNotes,
        OffsetDateTime contactedAt,
        OffsetDateTime confirmedAt,
        OffsetDateTime cancelledAt,
        OffsetDateTime createdAt
) {}
