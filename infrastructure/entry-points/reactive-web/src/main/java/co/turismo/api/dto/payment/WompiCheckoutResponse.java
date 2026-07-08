package co.turismo.api.dto.payment;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "WompiCheckoutResponse")
public record WompiCheckoutResponse(
        String reservationId,
        Long transactionId,
        String provider,
        String reference,
        Long amountInCents,
        String currency,
        String publicKey,
        String signatureIntegrity,
        String redirectUrl,
        String expirationTime,
        String customerEmail,
        String checkoutUrl,
        String status
) {}
