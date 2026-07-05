package co.turismo.usecase.payment;

import co.turismo.model.payment.PaymentCheckoutCommand;
import co.turismo.model.payment.PaymentCheckoutResult;
import co.turismo.model.payment.PaymentOrder;
import co.turismo.model.payment.PaymentReservation;
import co.turismo.model.payment.PaymentStatusResult;
import co.turismo.model.payment.gateways.PaymentGateway;
import co.turismo.model.payment.gateways.PaymentReservationGateway;
import co.turismo.model.tourpackage.CreateTourPackageRequest;
import co.turismo.model.tourpackage.TopPackage;
import co.turismo.model.tourpackage.TourPackage;
import co.turismo.model.tourpackage.TourPackageSalesSummary;
import co.turismo.model.tourpackage.UpdateTourPackageRequest;
import co.turismo.model.tourpackage.gateways.TourPackageRepository;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;

class PaymentUseCaseTest {

    @Test
    void shouldCreateCheckoutFromPackageAndCreatePendingReservation() {
        AtomicReference<PaymentOrder> capturedOrder = new AtomicReference<>();
        AtomicReference<PaymentReservation> capturedReservation = new AtomicReference<>();
        PaymentGateway paymentGateway = new PaymentGatewayStub(capturedOrder);
        PaymentReservationGateway reservationGateway = new PaymentReservationGatewayStub(capturedReservation);
        TourPackageRepository tourPackageRepository = new TourPackageRepositoryStub(TourPackage.builder()
                .id(3L)
                .title("Paquete de pruebas")
                .days(5)
                .price(1_890_000L)
                .build());
        PaymentUseCase useCase = new PaymentUseCase(paymentGateway, reservationGateway, tourPackageRepository);

        PaymentCheckoutCommand command = PaymentCheckoutCommand.builder()
                .tourPackageId(3L)
                .userEmail("ana@example.com")
                .startDate(LocalDate.now().plusDays(1))
                .build();

        StepVerifier.create(useCase.createCheckout(command))
                .expectNextMatches(result -> "pref-1".equals(result.getPreferenceId()))
                .verifyComplete();

        org.junit.jupiter.api.Assertions.assertEquals("Paquete de pruebas", capturedOrder.get().getPackageTitle());
        org.junit.jupiter.api.Assertions.assertEquals(BigDecimal.valueOf(1_890_000L), capturedOrder.get().getTotalPrice());
        org.junit.jupiter.api.Assertions.assertEquals("COP", capturedOrder.get().getCurrency());
        org.junit.jupiter.api.Assertions.assertEquals(3L, capturedReservation.get().getTourPackageId());
        org.junit.jupiter.api.Assertions.assertEquals(command.getStartDate().plusDays(5), capturedReservation.get().getEndDate());
    }

    @Test
    void shouldMarkReservationAsPaidWhenPaymentIsApproved() {
        AtomicReference<String> paidReservation = new AtomicReference<>();
        AtomicReference<String> paidPayment = new AtomicReference<>();
        PaymentGateway paymentGateway = new ApprovedPaymentGatewayStub();
        PaymentReservationGateway reservationGateway = new PaymentReservationGateway() {
            @Override
            public Mono<PaymentReservation> createPendingReservation(PaymentReservation reservation) {
                return Mono.just(reservation);
            }

            @Override
            public Mono<Void> markReservationAsPaid(String reservationId, String paymentId) {
            paidReservation.set(reservationId);
            paidPayment.set(paymentId);
            return Mono.empty();
            }
        };
        PaymentUseCase useCase = new PaymentUseCase(paymentGateway, reservationGateway, new EmptyTourPackageRepositoryStub());

        StepVerifier.create(useCase.processWebhook("123"))
                .expectNextMatches(PaymentStatusResult::isApproved)
                .verifyComplete();

        org.junit.jupiter.api.Assertions.assertEquals("reserva-1", paidReservation.get());
        org.junit.jupiter.api.Assertions.assertEquals("123", paidPayment.get());
    }

    @Test
    void shouldMarkReservationAsPaidWhenMerchantOrderHasApprovedPayment() {
        AtomicReference<String> paidReservation = new AtomicReference<>();
        AtomicReference<String> paidPayment = new AtomicReference<>();
        PaymentGateway paymentGateway = new ApprovedMerchantOrderGatewayStub();
        PaymentReservationGateway reservationGateway = new PaymentReservationGateway() {
            @Override
            public Mono<PaymentReservation> createPendingReservation(PaymentReservation reservation) {
                return Mono.just(reservation);
            }

            @Override
            public Mono<Void> markReservationAsPaid(String reservationId, String paymentId) {
            paidReservation.set(reservationId);
            paidPayment.set(paymentId);
            return Mono.empty();
            }
        };
        PaymentUseCase useCase = new PaymentUseCase(paymentGateway, reservationGateway, new EmptyTourPackageRepositoryStub());

        StepVerifier.create(useCase.processMerchantOrderWebhook("mo-123"))
                .expectNextMatches(PaymentStatusResult::isApproved)
                .verifyComplete();

        org.junit.jupiter.api.Assertions.assertEquals("reserva-tatacoa-001", paidReservation.get());
        org.junit.jupiter.api.Assertions.assertEquals("pay-123", paidPayment.get());
    }

    private record PaymentGatewayStub(AtomicReference<PaymentOrder> capturedOrder) implements PaymentGateway {
        @Override
        public Mono<PaymentCheckoutResult> createPreference(PaymentOrder order) {
            capturedOrder.set(order);
            return Mono.just(PaymentCheckoutResult.builder()
                    .preferenceId("pref-1")
                    .sandboxInitPoint("https://sandbox.mercadopago.com")
                    .build());
        }

        @Override
        public Mono<PaymentStatusResult> getPaymentStatus(String paymentId) {
            return Mono.empty();
        }

        @Override
        public Mono<PaymentStatusResult> getMerchantOrderPaymentStatus(String merchantOrderId) {
            return Mono.empty();
        }
    }

    private record PaymentReservationGatewayStub(
            AtomicReference<PaymentReservation> capturedReservation
    ) implements PaymentReservationGateway {
        @Override
        public Mono<PaymentReservation> createPendingReservation(PaymentReservation reservation) {
            capturedReservation.set(reservation);
            return Mono.just(reservation);
        }

        @Override
        public Mono<Void> markReservationAsPaid(String reservationId, String paymentId) {
            return Mono.empty();
        }
    }

    private static class TourPackageRepositoryStub implements TourPackageRepository {
        private final TourPackage tourPackage;

        private TourPackageRepositoryStub(TourPackage tourPackage) {
            this.tourPackage = tourPackage;
        }

        @Override
        public Mono<TourPackage> create(CreateTourPackageRequest request) {
            return Mono.empty();
        }

        @Override
        public Flux<TourPackage> findAll(Integer limit, Integer offset) {
            return Flux.empty();
        }

        @Override
        public Mono<TourPackage> findById(Long id) {
            return tourPackage.getId().equals(id) ? Mono.just(tourPackage) : Mono.empty();
        }

        @Override
        public Flux<TourPackage> findByAgencyId(Long agencyId, Integer limit, Integer offset) {
            return Flux.empty();
        }

        @Override
        public Flux<TopPackage> topSoldByAgency(Long agencyId, LocalDate from, LocalDate to, int limit) {
            return Flux.empty();
        }

        @Override
        public Mono<TourPackageSalesSummary> salesSummaryByAgency(Long agencyId, LocalDate from, LocalDate to) {
            return Mono.empty();
        }

        @Override
        public Mono<TourPackage> update(Long id, UpdateTourPackageRequest request) {
            return Mono.empty();
        }

        @Override
        public Mono<Void> delete(Long id) {
            return Mono.empty();
        }
    }

    private static class EmptyTourPackageRepositoryStub extends TourPackageRepositoryStub {
        private EmptyTourPackageRepositoryStub() {
            super(TourPackage.builder().id(-1L).build());
        }
    }

    private static class ApprovedPaymentGatewayStub implements PaymentGateway {
        @Override
        public Mono<PaymentCheckoutResult> createPreference(PaymentOrder order) {
            return Mono.empty();
        }

        @Override
        public Mono<PaymentStatusResult> getPaymentStatus(String paymentId) {
            return Mono.just(PaymentStatusResult.builder()
                    .paymentId(paymentId)
                    .status("approved")
                    .externalReference("reserva-1")
                    .build());
        }

        @Override
        public Mono<PaymentStatusResult> getMerchantOrderPaymentStatus(String merchantOrderId) {
            return Mono.empty();
        }
    }

    private static class ApprovedMerchantOrderGatewayStub implements PaymentGateway {
        @Override
        public Mono<PaymentCheckoutResult> createPreference(PaymentOrder order) {
            return Mono.empty();
        }

        @Override
        public Mono<PaymentStatusResult> getPaymentStatus(String paymentId) {
            return Mono.empty();
        }

        @Override
        public Mono<PaymentStatusResult> getMerchantOrderPaymentStatus(String merchantOrderId) {
            return Mono.just(PaymentStatusResult.builder()
                    .paymentId("pay-123")
                    .status("approved")
                    .externalReference("reserva-tatacoa-001")
                    .build());
        }
    }
}
