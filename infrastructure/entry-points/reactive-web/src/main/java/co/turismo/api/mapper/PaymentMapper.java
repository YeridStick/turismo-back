package co.turismo.api.mapper;

import co.turismo.api.dto.payment.PaymentStatusResponse;
import co.turismo.api.dto.payment.WompiCheckoutResponse;
import co.turismo.model.payment.PaymentStatusSnapshot;
import co.turismo.model.payment.WompiCheckoutData;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public final class PaymentMapper {

    private static final DateTimeFormatter WOMPI_EXPIRATION_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private PaymentMapper() {}

    public static WompiCheckoutResponse toCheckoutResponse(WompiCheckoutData data) {
        return new WompiCheckoutResponse(
                data.getReservationId(),
                data.getTransactionId(),
                data.getProvider(),
                data.getReference(),
                data.getAmountInCents(),
                data.getCurrency(),
                data.getPublicKey(),
                data.getSignatureIntegrity(),
                data.getRedirectUrl(),
                formatExpirationTime(data),
                data.getCustomerEmail(),
                data.getCheckoutUrl(),
                data.getStatus()
        );
    }

    public static PaymentStatusResponse toStatusResponse(PaymentStatusSnapshot snapshot) {
        return new PaymentStatusResponse(
                snapshot.getReservationId(),
                snapshot.getReservationStatus(),
                snapshot.getPaymentProvider(),
                snapshot.getPaymentStatus(),
                snapshot.getProviderTransactionId(),
                snapshot.getReference(),
                snapshot.getAmountInCents(),
                snapshot.getCurrency(),
                snapshot.getPaidAt(),
                snapshot.getExpiresAt()
        );
    }

    private static String formatExpirationTime(WompiCheckoutData data) {
        if (data.getExpirationTime() == null) {
            return null;
        }
        return WOMPI_EXPIRATION_FORMATTER.format(
                data.getExpirationTime().toInstant().truncatedTo(ChronoUnit.MILLIS));
    }
}
