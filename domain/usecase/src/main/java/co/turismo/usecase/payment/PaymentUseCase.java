package co.turismo.usecase.payment;

import co.turismo.model.error.ConflictException;
import co.turismo.model.error.NotFoundException;
import co.turismo.model.payment.PaymentEvent;
import co.turismo.model.payment.PaymentProvider;
import co.turismo.model.payment.PaymentStatus;
import co.turismo.model.payment.PaymentStatusSnapshot;
import co.turismo.model.payment.PaymentTransaction;
import co.turismo.model.payment.WompiCheckoutData;
import co.turismo.model.payment.WompiEventData;
import co.turismo.model.payment.gateways.PaymentEventRepository;
import co.turismo.model.payment.gateways.PaymentTransactionRepository;
import co.turismo.model.payment.gateways.WompiGateway;
import co.turismo.model.reservation.ReservationDraft;
import co.turismo.model.reservation.ReservationMessage;
import co.turismo.model.reservation.gateways.ReservationGateway;
import co.turismo.model.reservation.gateways.ReservationMessageGateway;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

@RequiredArgsConstructor
public class PaymentUseCase {

    private static final Logger LOG = Logger.getLogger(PaymentUseCase.class.getName());

    private static final String STATUS_AWAITING_PAYMENT = "awaiting_payment";
    private static final String STATUS_CONFIRMED = "confirmed";
    private static final String CURRENCY_COP = "COP";
    private static final String SENDER_SYSTEM = "SYSTEM";
    private static final String SYSTEM_SENDER_EMAIL = "system@turismo.local";

    private final ReservationGateway reservationGateway;
    private final ReservationMessageGateway reservationMessageGateway;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentEventRepository paymentEventRepository;
    private final WompiGateway wompiGateway;

    public Mono<WompiCheckoutData> createWompiCheckout(String userEmail, String reservationId) {
        if (!wompiGateway.isEnabled()) {
            return Mono.error(new ConflictException("Wompi no está habilitado"));
        }

        OffsetDateTime now = OffsetDateTime.now();
        return reservationGateway.findByIdForUser(requireText(reservationId, "reservationId requerido"), requireText(userEmail, "Usuario autenticado requerido"))
                .switchIfEmpty(Mono.error(new NotFoundException("Reserva no encontrada")))
                .flatMap(reservation -> validateCheckoutBase(reservation)
                        .then(Mono.defer(() -> paymentTransactionRepository.findReusableWompiTransaction(reservation.getId(), now)
                                .map(transaction -> wompiGateway.buildCheckout(transaction, reservation.getUserEmail()))
                                .switchIfEmpty(Mono.defer(() -> createNewCheckout(reservation, now))))));
    }

    public Mono<PaymentStatusSnapshot> findStatusForCustomer(String userEmail, String reservationId) {
        return reservationGateway.findByIdForUser(requireText(reservationId, "reservationId requerido"), requireText(userEmail, "Usuario autenticado requerido"))
                .switchIfEmpty(Mono.error(new NotFoundException("Reserva no encontrada")))
                .flatMap(this::buildStatusSnapshot);
    }

    public Mono<PaymentStatusSnapshot> findStatusForAgency(String agencyUserEmail, Long agencyId, boolean admin, String reservationId) {
        Mono<ReservationDraft> reservationMono;
        if (admin && (agencyId == null || agencyId <= 0)) {
            reservationMono = reservationGateway.findByIdForAdmin(requireText(reservationId, "reservationId requerido"));
        } else {
            reservationMono = reservationGateway.findByIdForAgency(requireText(reservationId, "reservationId requerido"), requireAgencyId(agencyId));
        }
        return reservationMono.switchIfEmpty(Mono.error(new NotFoundException("Reserva no encontrada")))
                .flatMap(this::buildStatusSnapshot);
    }

    public Mono<Void> handleWompiWebhook(String rawPayload, String eventChecksum) {
        return wompiGateway.parseAndValidateEvent(requireText(rawPayload, "payload requerido"), eventChecksum)
                .flatMap(event -> {
                    if (!event.isSignatureValid()) {
                        LOG.warning("Webhook Wompi rechazado por firma inválida");
                        return Mono.error(new IllegalArgumentException("Firma de webhook Wompi inválida"));
                    }
                    LOG.info(() -> "Webhook Wompi recibido. eventType=%s providerStatus=%s reference=%s"
                            .formatted(event.getEventType(), event.getProviderStatus(), event.getReference()));
                    PaymentEvent paymentEvent = PaymentEvent.builder()
                            .provider(PaymentProvider.WOMPI)
                            .eventId(event.getEventId())
                            .providerTransactionId(event.getProviderTransactionId())
                            .reference(event.getReference())
                            .eventType(normalize(event.getProviderStatus()))
                            .checksum(event.getHeaderChecksum() == null ? event.getChecksum() : event.getHeaderChecksum())
                            .payload(event.getRawPayload())
                            .processed(false)
                            .build();

                    return paymentEventRepository.saveIfAbsent(paymentEvent)
                            .flatMap(saved -> processWompiEvent(saved, event)
                                    .then(paymentEventRepository.markProcessed(saved.getId()))
                                    .then())
                            .switchIfEmpty(Mono.fromRunnable(() -> LOG.info(
                                    "Webhook Wompi duplicado ignorado. reference=" + event.getReference())));
                });
    }

    private Mono<WompiCheckoutData> createNewCheckout(ReservationDraft reservation, OffsetDateTime now) {
        String paymentStatus = normalize(reservation.getPaymentStatus());
        if (!PaymentStatus.WOMPI_CHECKOUT_ALLOWED.contains(paymentStatus)) {
            return Mono.error(new ConflictException("El estado de pago no permite iniciar checkout"));
        }

        long amountInCents = amountInCents(reservation.getTotalAmount());
        OffsetDateTime expiresAt = now.plusMinutes(wompiGateway.checkoutExpirationMinutes());
        String reference = buildReference(reservation.getId());

        PaymentTransaction baseTransaction = PaymentTransaction.builder()
                .reservationId(reservation.getId())
                .provider(PaymentProvider.WOMPI)
                .reference(reference)
                .amountInCents(amountInCents)
                .currency(CURRENCY_COP)
                .status(PaymentStatus.CHECKOUT_CREATED)
                .expiresAt(expiresAt)
                .requestPayload("{}")
                .build();

        WompiCheckoutData unsigned = wompiGateway.buildCheckout(baseTransaction, reservation.getUserEmail());
        PaymentTransaction transaction = baseTransaction.toBuilder()
                .checkoutUrl(unsigned.getCheckoutUrl())
                .requestPayload("""
                        {"reference":"%s","amountInCents":%d,"currency":"%s"}
                        """.formatted(reference, amountInCents, CURRENCY_COP).trim())
                .build();

        return paymentTransactionRepository.save(transaction)
                .flatMap(saved -> reservationGateway.markWompiCheckoutCreated(reservation.getId(), reservation.getUserEmail())
                        .thenReturn(saved))
                .map(saved -> wompiGateway.buildCheckout(saved, reservation.getUserEmail()));
    }

    private Mono<Void> validateCheckoutBase(ReservationDraft reservation) {
        if (!STATUS_AWAITING_PAYMENT.equals(normalize(reservation.getStatus()))) {
            return Mono.error(new ConflictException("La reserva aún no está habilitada para pago"));
        }
        if (!CURRENCY_COP.equalsIgnoreCase(normalize(reservation.getCurrency()))) {
            return Mono.error(new ConflictException("Wompi solo está habilitado para pagos en COP"));
        }
        if (reservation.getTotalAmount() == null || reservation.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return Mono.error(new ConflictException("La reserva no tiene un valor válido para pago"));
        }
        String paymentStatus = normalize(reservation.getPaymentStatus());
        if (PaymentStatus.PAID.equals(paymentStatus) || PaymentStatus.VERIFIED_BY_AGENCY.equals(paymentStatus)) {
            return Mono.error(new ConflictException("La reserva ya tiene un pago confirmado"));
        }
        return Mono.empty();
    }

    private Mono<PaymentStatusSnapshot> buildStatusSnapshot(ReservationDraft reservation) {
        return paymentTransactionRepository.findLatestByReservationId(reservation.getId())
                .map(transaction -> PaymentStatusSnapshot.builder()
                        .reservationId(reservation.getId())
                        .reservationStatus(reservation.getStatus())
                        .paymentProvider(reservation.getPaymentProvider())
                        .paymentStatus(reservation.getPaymentStatus())
                        .providerTransactionId(transaction.getProviderTransactionId())
                        .reference(transaction.getReference())
                        .amountInCents(transaction.getAmountInCents())
                        .currency(transaction.getCurrency())
                        .paidAt(transaction.getPaidAt() == null ? reservation.getPaidAt() : transaction.getPaidAt())
                        .expiresAt(transaction.getExpiresAt())
                        .build())
                .switchIfEmpty(Mono.just(PaymentStatusSnapshot.builder()
                        .reservationId(reservation.getId())
                        .reservationStatus(reservation.getStatus())
                        .paymentProvider(reservation.getPaymentProvider())
                        .paymentStatus(reservation.getPaymentStatus())
                        .currency(reservation.getCurrency())
                        .paidAt(reservation.getPaidAt())
                        .build()));
    }

    private Mono<Void> processWompiEvent(PaymentEvent savedEvent, WompiEventData event) {
        if (event.getReference() == null || event.getReference().isBlank()) {
            return paymentEventRepository.markFailed(savedEvent.getId(), "Referencia ausente").then();
        }

        return paymentTransactionRepository.findByReference(event.getReference())
                .switchIfEmpty(Mono.error(new NotFoundException("Transacción de pago no encontrada")))
                .flatMap(transaction -> applyProviderStatus(transaction, event)
                        .onErrorResume(error -> {
                            LOG.log(Level.WARNING, "No se pudo procesar webhook Wompi reference=" + event.getReference(), error);
                            return paymentEventRepository.markFailed(savedEvent.getId(), error.getMessage()).then(Mono.error(error));
                        }));
    }

    private Mono<Void> applyProviderStatus(PaymentTransaction transaction, WompiEventData event) {
        String providerStatus = normalize(event.getProviderStatus());
        String nextStatus = mapWompiStatus(providerStatus);
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime paidAt = PaymentStatus.PAID.equals(nextStatus) ? now : null;

        return paymentTransactionRepository.updateProviderResult(
                        transaction.getReference(),
                        event.getProviderTransactionId(),
                        providerStatus,
                        nextStatus,
                        event.getRawPayload(),
                        paidAt)
                .flatMap(updated -> {
                    if (PaymentStatus.PAID.equals(nextStatus)) {
                        return reservationGateway.applyWompiPaymentResult(
                                        updated.getReservationId(),
                                        PaymentStatus.PAID,
                                        event.getProviderTransactionId(),
                                        now,
                                        now,
                                        true)
                                .flatMap(reservation -> appendSystemMessageOnce(
                                        reservation.getId(),
                                        "Pago recibido por Wompi. Tu reserva fue confirmada."));
                    }
                    if (PaymentStatus.FAILED.equals(nextStatus)) {
                        return reservationGateway.applyWompiPaymentResult(
                                        updated.getReservationId(),
                                        PaymentStatus.FAILED,
                                        event.getProviderTransactionId(),
                                        null,
                                        null,
                                        false)
                                .flatMap(reservation -> appendSystemMessageOnce(
                                        reservation.getId(),
                                        "El pago no fue aprobado. Puedes intentarlo nuevamente."));
                    }
                    if (PaymentStatus.EXPIRED.equals(nextStatus)) {
                        return reservationGateway.applyWompiPaymentResult(
                                        updated.getReservationId(),
                                        PaymentStatus.EXPIRED,
                                        event.getProviderTransactionId(),
                                        null,
                                        null,
                                        false)
                                .flatMap(reservation -> appendSystemMessageOnce(
                                        reservation.getId(),
                                        "El checkout de pago expiró. Puedes generar un nuevo intento de pago."));
                    }
                    return Mono.empty();
                });
    }

    private Mono<Void> appendSystemMessageOnce(String reservationId, String body) {
        return reservationMessageGateway.save(ReservationMessage.builder()
                        .reservationId(reservationId)
                        .senderEmail(SYSTEM_SENDER_EMAIL)
                        .senderType(SENDER_SYSTEM)
                        .body(body)
                        .build())
                .then();
    }

    private static String mapWompiStatus(String providerStatus) {
        return switch (providerStatus) {
            case "approved" -> PaymentStatus.PAID;
            case "declined", "error", "voided" -> PaymentStatus.FAILED;
            case "expired" -> PaymentStatus.EXPIRED;
            default -> PaymentStatus.PROCESSING;
        };
    }

    private static long amountInCents(BigDecimal amount) {
        return amount.multiply(BigDecimal.valueOf(100L))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    private static String buildReference(String reservationId) {
        String safeId = reservationId == null ? "reserva" : reservationId.replaceAll("[^A-Za-z0-9-]", "");
        if (safeId.length() > 36) {
            safeId = safeId.substring(0, 36);
        }
        return "wompi-" + safeId + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static Long requireAgencyId(Long agencyId) {
        if (agencyId == null || agencyId <= 0) {
            throw new IllegalArgumentException("agencyId requerido");
        }
        return agencyId;
    }
}
