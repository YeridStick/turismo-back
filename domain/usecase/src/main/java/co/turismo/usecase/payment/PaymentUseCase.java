package co.turismo.usecase.payment;

import co.turismo.model.payment.PaymentCheckoutResult;
import co.turismo.model.payment.PaymentCheckoutCommand;
import co.turismo.model.payment.PaymentOrder;
import co.turismo.model.payment.PaymentReservation;
import co.turismo.model.payment.PaymentStatusResult;
import co.turismo.model.error.ConflictException;
import co.turismo.model.error.NotFoundException;
import co.turismo.model.payment.gateways.PaymentGateway;
import co.turismo.model.payment.gateways.PaymentReservationGateway;
import co.turismo.model.tourpackage.TourPackage;
import co.turismo.model.tourpackage.gateways.TourPackageRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@RequiredArgsConstructor
public class PaymentUseCase {

    private static final String DEFAULT_CURRENCY = "COP";
    private static final String PENDING_PAYMENT = "pending_payment";

    private final PaymentGateway paymentGateway;
    private final PaymentReservationGateway paymentReservationGateway;
    private final TourPackageRepository tourPackageRepository;

    public Mono<PaymentCheckoutResult> createCheckout(PaymentCheckoutCommand command) {
        return validateCheckoutCommand(command)
                .then(tourPackageRepository.findById(command.getTourPackageId()))
                .switchIfEmpty(Mono.error(new NotFoundException("Paquete turístico no encontrado")))
                .flatMap(tourPackage -> buildReservation(command, tourPackage))
                .flatMap(paymentReservationGateway::createPendingReservation)
                .map(this::toPaymentOrder)
                .flatMap(paymentGateway::createPreference);
    }

    public Mono<PaymentStatusResult> processWebhook(String paymentId) {
        return paymentGateway.getPaymentStatus(paymentId)
                .flatMap(this::markReservationIfApproved);
    }

    public Mono<PaymentStatusResult> processMerchantOrderWebhook(String merchantOrderId) {
        return paymentGateway.getMerchantOrderPaymentStatus(merchantOrderId)
                .flatMap(this::markReservationIfApproved);
    }

    private Mono<PaymentStatusResult> markReservationIfApproved(PaymentStatusResult status) {
        if (!status.isApproved() || !hasText(status.getExternalReference())) {
            return Mono.just(status);
        }
        return paymentReservationGateway
                .markReservationAsPaid(status.getExternalReference(), status.getPaymentId())
                .thenReturn(status);
    }

    private Mono<Void> validateCheckoutCommand(PaymentCheckoutCommand command) {
        if (command == null) {
            return Mono.error(new IllegalArgumentException("Solicitud de checkout requerida"));
        }
        if (command.getTourPackageId() == null || command.getTourPackageId() <= 0) {
            return Mono.error(new IllegalArgumentException("tourPackageId es obligatorio"));
        }
        if (command.getStartDate() == null) {
            return Mono.error(new IllegalArgumentException("startDate es obligatorio"));
        }
        if (command.getStartDate().isBefore(LocalDate.now())) {
            return Mono.error(new ConflictException("La fecha de inicio no puede estar en el pasado"));
        }
        return Mono.empty();
    }

    private Mono<PaymentReservation> buildReservation(PaymentCheckoutCommand command, TourPackage tourPackage) {
        if (tourPackage.getPrice() == null || tourPackage.getPrice() <= 0) {
            return Mono.error(new ConflictException("El paquete turístico no tiene precio válido"));
        }
        int days = tourPackage.getDays() == null || tourPackage.getDays() <= 0 ? 1 : tourPackage.getDays();
        LocalDate endDate = command.getStartDate().plusDays(days);

        return Mono.just(PaymentReservation.builder()
                .id("reserva-" + UUID.randomUUID())
                .userEmail(command.getUserEmail())
                .tourPackageId(tourPackage.getId())
                .packageTitle(tourPackage.getTitle())
                .totalAmount(BigDecimal.valueOf(tourPackage.getPrice()))
                .currency(DEFAULT_CURRENCY)
                .startDate(command.getStartDate())
                .endDate(endDate)
                .status(PENDING_PAYMENT)
                .build());
    }

    private PaymentOrder toPaymentOrder(PaymentReservation reservation) {
        return PaymentOrder.builder()
                .reservationId(reservation.getId())
                .tourPackageId(reservation.getTourPackageId())
                .packageTitle(reservation.getPackageTitle())
                .totalPrice(reservation.getTotalAmount())
                .currency(reservation.getCurrency())
                .build();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
