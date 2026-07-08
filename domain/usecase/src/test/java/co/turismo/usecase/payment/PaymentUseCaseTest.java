package co.turismo.usecase.payment;

import co.turismo.model.error.ConflictException;
import co.turismo.model.payment.PaymentEvent;
import co.turismo.model.payment.PaymentProvider;
import co.turismo.model.payment.PaymentStatus;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentUseCaseTest {

    @Mock
    private ReservationGateway reservationGateway;
    @Mock
    private ReservationMessageGateway reservationMessageGateway;
    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;
    @Mock
    private PaymentEventRepository paymentEventRepository;
    @Mock
    private WompiGateway wompiGateway;

    private PaymentUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new PaymentUseCase(
                reservationGateway,
                reservationMessageGateway,
                paymentTransactionRepository,
                paymentEventRepository,
                wompiGateway);
    }

    @Test
    void createCheckoutShouldGenerateWompiTransactionForValidReservation() {
        ArgumentCaptor<PaymentTransaction> captor = ArgumentCaptor.forClass(PaymentTransaction.class);
        ReservationDraft reservation = awaitingPaymentReservation();

        when(wompiGateway.isEnabled()).thenReturn(true);
        when(wompiGateway.checkoutExpirationMinutes()).thenReturn(30L);
        when(reservationGateway.findByIdForUser(reservation.getId(), "user@example.com")).thenReturn(Mono.just(reservation));
        when(paymentTransactionRepository.findReusableWompiTransaction(eq(reservation.getId()), any())).thenReturn(Mono.empty());
        when(wompiGateway.buildCheckout(any(), eq("user@example.com"))).thenAnswer(invocation -> {
            PaymentTransaction tx = invocation.getArgument(0);
            return WompiCheckoutData.builder()
                    .reservationId(tx.getReservationId())
                    .transactionId(tx.getId())
                    .provider(PaymentProvider.WOMPI)
                    .reference(tx.getReference())
                    .amountInCents(tx.getAmountInCents())
                    .currency(tx.getCurrency())
                    .checkoutUrl("https://checkout.wompi.co/p/?reference=" + tx.getReference())
                    .status(tx.getStatus())
                    .build();
        });
        when(paymentTransactionRepository.save(any())).thenAnswer(invocation -> {
            PaymentTransaction tx = invocation.getArgument(0);
            return Mono.just(tx.toBuilder().id(10L).build());
        });
        when(reservationGateway.markWompiCheckoutCreated(reservation.getId(), reservation.getUserEmail())).thenReturn(Mono.just(reservation));

        StepVerifier.create(useCase.createWompiCheckout("user@example.com", reservation.getId()))
                .assertNext(response -> {
                    assertEquals(10L, response.getTransactionId());
                    assertEquals(PaymentProvider.WOMPI, response.getProvider());
                    assertEquals(25_000_000L, response.getAmountInCents());
                    assertEquals("COP", response.getCurrency());
                    assertTrue(response.getCheckoutUrl().contains("checkout.wompi.co"));
                })
                .verifyComplete();

        verify(paymentTransactionRepository).save(captor.capture());
        assertEquals(PaymentStatus.CHECKOUT_CREATED, captor.getValue().getStatus());
        assertEquals(25_000_000L, captor.getValue().getAmountInCents());
    }

    @Test
    void createCheckoutShouldRejectReservationThatIsNotAwaitingPayment() {
        ReservationDraft reservation = awaitingPaymentReservation().toBuilder()
                .status("contacted")
                .build();

        when(wompiGateway.isEnabled()).thenReturn(true);
        when(reservationGateway.findByIdForUser(reservation.getId(), "user@example.com")).thenReturn(Mono.just(reservation));

        StepVerifier.create(useCase.createWompiCheckout("user@example.com", reservation.getId()))
                .expectError(ConflictException.class)
                .verify();

        verify(paymentTransactionRepository, never()).save(any());
    }

    @Test
    void createCheckoutShouldRecoverStaleCheckoutCreatedPaymentStatus() {
        ReservationDraft reservation = awaitingPaymentReservation().toBuilder()
                .paymentStatus(PaymentStatus.CHECKOUT_CREATED)
                .build();

        when(wompiGateway.isEnabled()).thenReturn(true);
        when(wompiGateway.checkoutExpirationMinutes()).thenReturn(30L);
        when(reservationGateway.findByIdForUser(reservation.getId(), "user@example.com")).thenReturn(Mono.just(reservation));
        when(paymentTransactionRepository.findReusableWompiTransaction(eq(reservation.getId()), any())).thenReturn(Mono.empty());
        when(wompiGateway.buildCheckout(any(), eq("user@example.com"))).thenAnswer(invocation -> {
            PaymentTransaction tx = invocation.getArgument(0);
            return WompiCheckoutData.builder()
                    .reservationId(tx.getReservationId())
                    .transactionId(tx.getId())
                    .provider(PaymentProvider.WOMPI)
                    .reference(tx.getReference())
                    .amountInCents(tx.getAmountInCents())
                    .currency(tx.getCurrency())
                    .status(tx.getStatus())
                    .build();
        });
        when(paymentTransactionRepository.save(any())).thenAnswer(invocation -> {
            PaymentTransaction tx = invocation.getArgument(0);
            return Mono.just(tx.toBuilder().id(11L).build());
        });
        when(reservationGateway.markWompiCheckoutCreated(reservation.getId(), reservation.getUserEmail())).thenReturn(Mono.just(reservation));

        StepVerifier.create(useCase.createWompiCheckout("user@example.com", reservation.getId()))
                .assertNext(response -> assertEquals(11L, response.getTransactionId()))
                .verifyComplete();

        verify(paymentTransactionRepository).save(any());
    }

    @Test
    void createCheckoutShouldRejectReservationFromAnotherUser() {
        when(wompiGateway.isEnabled()).thenReturn(true);
        when(reservationGateway.findByIdForUser("reserva-1", "other@example.com")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.createWompiCheckout("other@example.com", "reserva-1"))
                .expectError()
                .verify();

        verify(paymentTransactionRepository, never()).save(any());
    }

    @Test
    void approvedWebhookShouldConfirmReservation() {
        WompiEventData event = event("APPROVED");
        PaymentEvent savedEvent = PaymentEvent.builder().id(1L).provider(PaymentProvider.WOMPI).build();
        PaymentTransaction transaction = transaction(PaymentStatus.CHECKOUT_CREATED);
        PaymentTransaction paid = transaction.toBuilder()
                .status(PaymentStatus.PAID)
                .providerTransactionId("wompi-tx-1")
                .paidAt(OffsetDateTime.now())
                .build();

        when(wompiGateway.parseAndValidateEvent("{}", null)).thenReturn(Mono.just(event));
        when(paymentEventRepository.saveIfAbsent(any())).thenReturn(Mono.just(savedEvent));
        when(paymentTransactionRepository.findByReference("ref-1")).thenReturn(Mono.just(transaction));
        when(paymentTransactionRepository.updateProviderResult(eq("ref-1"), eq("wompi-tx-1"), eq("approved"), eq(PaymentStatus.PAID), eq("{}"), any()))
                .thenReturn(Mono.just(paid));
        when(reservationGateway.applyWompiPaymentResult(eq("reserva-1"), eq(PaymentStatus.PAID), eq("wompi-tx-1"), any(), any(), eq(true)))
                .thenReturn(Mono.just(awaitingPaymentReservation().toBuilder().status("confirmed").paymentStatus(PaymentStatus.PAID).build()));
        when(reservationMessageGateway.save(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(paymentEventRepository.markProcessed(1L)).thenReturn(Mono.just(true));

        StepVerifier.create(useCase.handleWompiWebhook("{}", null))
                .verifyComplete();

        verify(reservationGateway).applyWompiPaymentResult(
                eq("reserva-1"),
                eq(PaymentStatus.PAID),
                eq("wompi-tx-1"),
                any(),
                any(),
                eq(true));
        ArgumentCaptor<ReservationMessage> messageCaptor = ArgumentCaptor.forClass(ReservationMessage.class);
        verify(reservationMessageGateway).save(messageCaptor.capture());
        assertTrue(messageCaptor.getValue().getBody().contains("Pago recibido por Wompi"));
    }

    @Test
    void declinedWebhookShouldNotConfirmReservation() {
        WompiEventData event = event("DECLINED");
        PaymentEvent savedEvent = PaymentEvent.builder().id(2L).provider(PaymentProvider.WOMPI).build();
        PaymentTransaction transaction = transaction(PaymentStatus.CHECKOUT_CREATED);

        when(wompiGateway.parseAndValidateEvent("{}", null)).thenReturn(Mono.just(event));
        when(paymentEventRepository.saveIfAbsent(any())).thenReturn(Mono.just(savedEvent));
        when(paymentTransactionRepository.findByReference("ref-1")).thenReturn(Mono.just(transaction));
        when(paymentTransactionRepository.updateProviderResult(eq("ref-1"), eq("wompi-tx-1"), eq("declined"), eq(PaymentStatus.FAILED), eq("{}"), eq(null)))
                .thenReturn(Mono.just(transaction.toBuilder().status(PaymentStatus.FAILED).build()));
        when(reservationGateway.applyWompiPaymentResult(eq("reserva-1"), eq(PaymentStatus.FAILED), eq("wompi-tx-1"), eq(null), eq(null), eq(false)))
                .thenReturn(Mono.just(awaitingPaymentReservation().toBuilder().paymentStatus(PaymentStatus.FAILED).build()));
        when(reservationMessageGateway.save(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(paymentEventRepository.markProcessed(2L)).thenReturn(Mono.just(true));

        StepVerifier.create(useCase.handleWompiWebhook("{}", null))
                .verifyComplete();

        verify(reservationGateway).applyWompiPaymentResult(eq("reserva-1"), eq(PaymentStatus.FAILED), eq("wompi-tx-1"), eq(null), eq(null), eq(false));
    }

    @Test
    void duplicateWebhookShouldNotCreateMessageAgain() {
        when(wompiGateway.parseAndValidateEvent("{}", null)).thenReturn(Mono.just(event("APPROVED")));
        when(paymentEventRepository.saveIfAbsent(any())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.handleWompiWebhook("{}", null))
                .verifyComplete();

        verify(reservationMessageGateway, never()).save(any());
    }

    private static ReservationDraft awaitingPaymentReservation() {
        return ReservationDraft.builder()
                .id("reserva-1")
                .userEmail("user@example.com")
                .tourPackageId(6L)
                .agencyId(2L)
                .packageTitle("Paquete")
                .totalAmount(BigDecimal.valueOf(250000L))
                .currency("COP")
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(2))
                .status("awaiting_payment")
                .paymentProvider(PaymentProvider.AGENCY_MANAGED)
                .paymentStatus(PaymentStatus.PENDING)
                .build();
    }

    private static PaymentTransaction transaction(String status) {
        return PaymentTransaction.builder()
                .id(10L)
                .reservationId("reserva-1")
                .provider(PaymentProvider.WOMPI)
                .reference("ref-1")
                .amountInCents(25_000_000L)
                .currency("COP")
                .status(status)
                .build();
    }

    private static WompiEventData event(String status) {
        return WompiEventData.builder()
                .eventId("evt-1")
                .eventType("transaction.updated")
                .providerTransactionId("wompi-tx-1")
                .reference("ref-1")
                .providerStatus(status)
                .amountInCents(25_000_000L)
                .currency("COP")
                .checksum("checksum")
                .rawPayload("{}")
                .signatureValid(true)
                .build();
    }
}
