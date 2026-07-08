package co.turismo.api.dto.payment;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

@Schema(name = "PaymentStatusResponse")
public record PaymentStatusResponse(
        String reservationId,
        String reservationStatus,
        String paymentProvider,
        String paymentStatus,
        String providerTransactionId,
        String reference,
        Long amountInCents,
        String currency,
        OffsetDateTime paidAt,
        OffsetDateTime expiresAt
) {}
