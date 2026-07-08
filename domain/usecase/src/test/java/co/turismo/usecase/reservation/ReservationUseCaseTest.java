package co.turismo.usecase.reservation;

import co.turismo.model.agency.Agency;
import co.turismo.model.agency.gateways.AgencyRepository;
import co.turismo.model.error.ConflictException;
import co.turismo.model.error.NotFoundException;
import co.turismo.model.notification.EmailMessage;
import co.turismo.model.notification.gateways.AppNotificationGateway;
import co.turismo.model.notification.gateways.EmailGateway;
import co.turismo.model.payment.gateways.PaymentTransactionRepository;
import co.turismo.model.reservation.ReservationDraft;
import co.turismo.model.reservation.ReservationMessage;
import co.turismo.model.reservation.ReservationRequestDetails;
import co.turismo.model.reservation.ReservationStatusChange;
import co.turismo.model.reservation.gateways.ReservationGateway;
import co.turismo.model.reservation.gateways.ReservationMessageGateway;
import co.turismo.model.tourpackage.TourPackage;
import co.turismo.model.tourpackage.gateways.TourPackageRepository;
import co.turismo.model.user.gateways.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationUseCaseTest {

    @Mock
    private ReservationGateway reservationGateway;
    @Mock
    private TourPackageRepository tourPackageRepository;
    @Mock
    private AgencyRepository agencyRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EmailGateway emailGateway;
    @Mock
    private AppNotificationGateway appNotificationGateway;
    @Mock
    private ReservationMessageGateway reservationMessageGateway;
    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    private ReservationUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ReservationUseCase(
                reservationGateway,
                tourPackageRepository,
                agencyRepository,
                userRepository,
                emailGateway,
                appNotificationGateway,
                reservationMessageGateway,
                paymentTransactionRepository
        );
        lenient().when(userRepository.isEmailVerified("user@example.com")).thenReturn(Mono.just(false));
        lenient().when(userRepository.findByAgencyId(2L)).thenReturn(Flux.empty());
        lenient().when(userRepository.findByRoleName("ADMIN")).thenReturn(Flux.empty());
        lenient().when(appNotificationGateway.save(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        lenient().when(reservationMessageGateway.save(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        lenient().when(paymentTransactionRepository.existsPaidWompiTransaction(any())).thenReturn(Mono.just(false));
        lenient().when(paymentTransactionRepository.existsBlockingWompiTransaction(any(), any())).thenReturn(Mono.just(false));
    }

    @Test
    void createRequestShouldUseBackendDataAndKeepPaymentFieldsEmpty() {
        TourPackage tourPackage = activePackage();
        ArgumentCaptor<ReservationDraft> captor = ArgumentCaptor.forClass(ReservationDraft.class);

        when(tourPackageRepository.findById(6L)).thenReturn(Mono.just(tourPackage));
        when(reservationGateway.createPendingReservation(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(useCase.createRequest(validDetails()))
                .assertNext(created -> {
                    assertTrue(created.getId().startsWith("reserva-"));
                    assertEquals(6L, created.getTourPackageId());
                    assertEquals(2L, created.getAgencyId());
                    assertEquals("Paquete de pruebas", created.getPackageTitle());
                    assertEquals(BigDecimal.valueOf(250000L), created.getTotalAmount());
                    assertEquals("COP", created.getCurrency());
                    assertEquals("requested", created.getStatus());
                    assertEquals("agency_managed", created.getPaymentProvider());
                    assertEquals("pending", created.getPaymentStatus());
                    assertEquals("IN_APP", created.getContactPreference());
                    assertNull(created.getCustomerPhone());
                    assertNull(created.getPaymentId());
                    assertNull(created.getPaidAt());
                    assertNotNull(created.getConsentAcceptedAt());
                })
                .verifyComplete();

        verify(reservationGateway).createPendingReservation(captor.capture());
        assertEquals("user@example.com", captor.getValue().getUserEmail());
    }

    @Test
    void createRequestShouldSendEmailWhenUserEmailIsVerified() {
        ArgumentCaptor<EmailMessage> emailCaptor = ArgumentCaptor.forClass(EmailMessage.class);

        when(tourPackageRepository.findById(6L)).thenReturn(Mono.just(activePackage()));
        when(reservationGateway.createPendingReservation(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(userRepository.isEmailVerified("user@example.com")).thenReturn(Mono.just(true));
        when(emailGateway.sendEmail(any(EmailMessage.class))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.createRequest(validDetails().toBuilder()
                        .contactPreference("EMAIL")
                        .build()))
                .assertNext(created -> assertEquals("requested", created.getStatus()))
                .verifyComplete();

        verify(emailGateway).sendEmail(emailCaptor.capture());
        EmailMessage email = emailCaptor.getValue();
        assertEquals("user@example.com", email.to());
        assertEquals("Solicitud de reserva creada - Turismo Huila", email.subject());
        assertTrue(email.htmlBody().contains("Paquete de pruebas"));
        assertTrue(email.htmlBody().contains("requested"));
    }

    @Test
    void createRequestShouldSkipEmailWhenUserEmailIsNotVerified() {
        when(tourPackageRepository.findById(6L)).thenReturn(Mono.just(activePackage()));
        when(reservationGateway.createPendingReservation(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        StepVerifier.create(useCase.createRequest(validDetails()))
                .assertNext(created -> assertEquals("requested", created.getStatus()))
                .verifyComplete();

        verifyNoInteractions(emailGateway);
    }

    @Test
    void createRequestShouldRejectMissingPackage() {
        when(tourPackageRepository.findById(6L)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.createRequest(validDetails()))
                .expectError(NotFoundException.class)
                .verify();
    }

    @Test
    void createRequestShouldRejectInactivePackage() {
        TourPackage inactive = activePackage().toBuilder().isActive(false).build();
        when(tourPackageRepository.findById(6L)).thenReturn(Mono.just(inactive));

        StepVerifier.create(useCase.createRequest(validDetails()))
                .expectError(ConflictException.class)
                .verify();
    }

    @Test
    void createRequestShouldAcceptValidContactPreferencesAndNormalizeThem() {
        when(tourPackageRepository.findById(6L)).thenReturn(Mono.just(activePackage()));
        when(reservationGateway.createPendingReservation(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        assertContactPreference("IN_APP", "IN_APP");
        assertContactPreference("in_app", "IN_APP");
        assertContactPreference("email", "EMAIL");
    }

    @Test
    void createRequestShouldRejectInvalidContactPreferencesAsValidationErrors() {
        assertValidationError(validDetails().toBuilder().contactPreference("INVALID").build());
        assertValidationError(validDetails().toBuilder().contactPreference("WHATSAPP").build());
        assertValidationError(validDetails().toBuilder().contactPreference("PHONE").build());
        assertValidationError(validDetails().toBuilder().contactPreference("SMS").build());
        assertValidationError(validDetails().toBuilder().contactPreference("TELEGRAM").build());
        assertValidationError(validDetails().toBuilder().contactPreference(null).build());
        assertValidationError(validDetails().toBuilder().contactPreference(" ").build());
    }

    @Test
    void createRequestShouldCalculateInclusiveEndDateFromPackageDays() {
        LocalDate startDate = LocalDate.now().plusDays(5);
        when(reservationGateway.createPendingReservation(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        when(tourPackageRepository.findById(6L)).thenReturn(Mono.just(activePackage().toBuilder().days(1).build()));
        StepVerifier.create(useCase.createRequest(validDetails().toBuilder()
                        .startDate(startDate)
                        .endDate(null)
                        .build()))
                .assertNext(created -> assertEquals(startDate, created.getEndDate()))
                .verifyComplete();

        when(tourPackageRepository.findById(6L)).thenReturn(Mono.just(activePackage().toBuilder().days(3).build()));
        StepVerifier.create(useCase.createRequest(validDetails().toBuilder()
                        .startDate(startDate)
                        .endDate(null)
                        .build()))
                .assertNext(created -> assertEquals(startDate.plusDays(2), created.getEndDate()))
                .verifyComplete();
    }

    @Test
    void createRequestShouldRespectExplicitEndDate() {
        LocalDate startDate = LocalDate.now().plusDays(5);
        LocalDate explicitEndDate = startDate.plusDays(4);
        when(tourPackageRepository.findById(6L)).thenReturn(Mono.just(activePackage().toBuilder().days(3).build()));
        when(reservationGateway.createPendingReservation(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(useCase.createRequest(validDetails().toBuilder()
                        .startDate(startDate)
                        .endDate(explicitEndDate)
                        .build()))
                .assertNext(created -> assertEquals(explicitEndDate, created.getEndDate()))
                .verifyComplete();
    }

    @Test
    void createRequestShouldRejectInvalidPackageDurationAsValidationError() {
        assertInvalidPackageDays(null);
        assertInvalidPackageDays(0);
        assertInvalidPackageDays(-1);
    }

    @Test
    void createRequestShouldRejectInvalidDatesTravelersAndConsentAsValidationErrors() {
        StepVerifier.create(useCase.createRequest(validDetails().toBuilder()
                        .startDate(LocalDate.now().minusDays(1))
                        .build()))
                .expectError(IllegalArgumentException.class)
                .verify();

        StepVerifier.create(useCase.createRequest(validDetails().toBuilder()
                        .endDate(LocalDate.now().minusDays(1))
                        .build()))
                .expectError(IllegalArgumentException.class)
                .verify();

        StepVerifier.create(useCase.createRequest(validDetails().toBuilder()
                        .travelers(0)
                        .build()))
                .expectError(IllegalArgumentException.class)
                .verify();

        StepVerifier.create(useCase.createRequest(validDetails().toBuilder()
                        .travelers(-1)
                        .build()))
                .expectError(IllegalArgumentException.class)
                .verify();

        StepVerifier.create(useCase.createRequest(validDetails().toBuilder()
                        .customerPhone("1234567890123456789012345678901")
                        .build()))
                .expectError(IllegalArgumentException.class)
                .verify();

        StepVerifier.create(useCase.createRequest(validDetails().toBuilder()
                        .consentAccepted(false)
                        .build()))
                .expectError(IllegalArgumentException.class)
                .verify();

        StepVerifier.create(useCase.createRequest(validDetails().toBuilder()
                        .consentVersion(" ")
                        .build()))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    void findMineShouldUseAuthenticatedUserAndPagination() {
        ReservationDraft reservation = reservation("requested");
        when(reservationGateway.findByUserEmail("user@example.com", 20, 40)).thenReturn(Flux.just(reservation));

        StepVerifier.create(useCase.findMine("user@example.com", 20, 40))
                .expectNext(reservation)
                .verifyComplete();

        verify(reservationGateway).findByUserEmail("user@example.com", 20, 40);
    }

    @Test
    void findMineByIdShouldReturnNotFoundForOtherUsersReservations() {
        when(reservationGateway.findByIdForUser("reserva-1", "user@example.com")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.findMineById("user@example.com", "reserva-1"))
                .expectError(NotFoundException.class)
                .verify();
    }

    @Test
    void agencyListShouldResolveAgencyFromAuthenticatedUserAndFilterByStatus() {
        ReservationDraft reservation = reservation("requested");
        when(agencyRepository.findByUserEmail("agency@example.com"))
                .thenReturn(Flux.just(Agency.builder().id(2L).build()));
        when(reservationGateway.findByAgencyId(2L, "requested", 10, 20)).thenReturn(Flux.just(reservation));

        StepVerifier.create(useCase.findForMyAgency("agency@example.com", "REQUESTED", 10, 20))
                .expectNext(reservation)
                .verifyComplete();

        verify(reservationGateway).findByAgencyId(2L, "requested", 10, 20);
    }

    @Test
    void agencyFindByIdShouldReturnNotFoundForOtherAgencyReservations() {
        when(agencyRepository.findByUserEmail("agency@example.com"))
                .thenReturn(Flux.just(Agency.builder().id(2L).build()));
        when(reservationGateway.findByIdForAgency("reserva-1", 2L)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.findForMyAgencyById("agency@example.com", "reserva-1"))
                .expectError(NotFoundException.class)
                .verify();
    }

    @Test
    void validStatusTransitionShouldUpdateReservation() {
        ReservationDraft current = reservation("contacted");
        ReservationDraft updated = reservation("awaiting_payment");
        when(agencyRepository.findByUserEmail("agency@example.com"))
                .thenReturn(Flux.just(Agency.builder().id(2L).build()));
        when(reservationGateway.findByIdForAgency("reserva-1", 2L)).thenReturn(Mono.just(current));
        when(reservationGateway.updateAgencyStatus(
                eq("reserva-1"),
                eq(2L),
                eq("awaiting_payment"),
                eq("Instrucciones enviadas"),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null)
        )).thenReturn(Mono.just(updated));

        StepVerifier.create(useCase.updateAgencyStatus(ReservationStatusChange.builder()
                        .reservationId("reserva-1")
                        .agencyUserEmail("agency@example.com")
                        .status("awaiting_payment")
                        .notes("Instrucciones enviadas")
                        .build()))
                .assertNext(result -> assertEquals("awaiting_payment", result.getStatus()))
                .verifyComplete();

        ArgumentCaptor<ReservationMessage> messageCaptor = ArgumentCaptor.forClass(ReservationMessage.class);
        verify(reservationMessageGateway).save(messageCaptor.capture());
        assertEquals("reserva-1", messageCaptor.getValue().getReservationId());
        assertEquals("SYSTEM", messageCaptor.getValue().getSenderType());
        assertTrue(messageCaptor.getValue().getBody().contains("esperando pago"));
        assertTrue(messageCaptor.getValue().getBody().contains("Instrucciones enviadas"));
    }

    @Test
    void requestedReservationShouldNotBeManuallyMarkedAsContacted() {
        ReservationDraft current = reservation("requested");
        when(agencyRepository.findByUserEmail("agency@example.com"))
                .thenReturn(Flux.just(Agency.builder().id(2L).build()));
        when(reservationGateway.findByIdForAgency("reserva-1", 2L)).thenReturn(Mono.just(current));

        StepVerifier.create(useCase.updateAgencyStatus(ReservationStatusChange.builder()
                        .reservationId("reserva-1")
                        .agencyUserEmail("agency@example.com")
                        .status("contacted")
                        .notes("Se contactó")
                        .build()))
                .expectError(ConflictException.class)
                .verify();
    }

    @Test
    void confirmedStatusShouldVerifyAgencyManagedPaymentWithoutFakePaymentId() {
        ReservationDraft current = reservation("awaiting_payment");
        ReservationDraft updated = reservation("confirmed").toBuilder()
                .paymentStatus("verified_by_agency")
                .paymentProvider("agency_managed")
                .paymentId(null)
                .paidAt(OffsetDateTime.now())
                .build();
        when(agencyRepository.findByUserEmail("agency@example.com"))
                .thenReturn(Flux.just(Agency.builder().id(2L).build()));
        when(reservationGateway.findByIdForAgency("reserva-1", 2L)).thenReturn(Mono.just(current));
        when(reservationGateway.updateAgencyStatus(
                eq("reserva-1"),
                eq(2L),
                eq("confirmed"),
                eq(null),
                eq("agency_managed"),
                eq("verified_by_agency"),
                any(OffsetDateTime.class),
                eq(null),
                any(OffsetDateTime.class),
                eq(null)
        )).thenReturn(Mono.just(updated));

        StepVerifier.create(useCase.updateAgencyStatus(ReservationStatusChange.builder()
                        .reservationId("reserva-1")
                        .agencyUserEmail("agency@example.com")
                        .status("confirmed")
                        .build()))
                .assertNext(result -> {
                    assertEquals("verified_by_agency", result.getPaymentStatus());
                    assertNull(result.getPaymentId());
                    assertNotNull(result.getPaidAt());
                })
                .verifyComplete();
    }

    @Test
    void invalidTransitionsAndHistoricalStatusesShouldBeRejectedButReadable() {
        ReservationDraft paid = reservation("paid").toBuilder()
                .agencyId(null)
                .travelers(null)
                .paymentProvider(null)
                .paymentStatus(null)
                .build();
        ReservationDraft pendingPayment = reservation("pending_payment").toBuilder()
                .agencyId(null)
                .travelers(null)
                .paymentProvider(null)
                .paymentStatus(null)
                .build();
        when(reservationGateway.findByIdForUser("reserva-paid", "user@example.com")).thenReturn(Mono.just(paid));
        when(reservationGateway.findByIdForUser("reserva-pending", "user@example.com"))
                .thenReturn(Mono.just(pendingPayment));

        StepVerifier.create(useCase.findMineById("user@example.com", "reserva-paid"))
                .expectNext(paid)
                .verifyComplete();

        StepVerifier.create(useCase.findMineById("user@example.com", "reserva-pending"))
                .expectNext(pendingPayment)
                .verifyComplete();

        when(agencyRepository.findByUserEmail("agency@example.com"))
                .thenReturn(Flux.just(Agency.builder().id(2L).build()));
        when(reservationGateway.findByIdForAgency("reserva-1", 2L)).thenReturn(Mono.just(reservation("rejected")));

        StepVerifier.create(useCase.updateAgencyStatus(ReservationStatusChange.builder()
                        .reservationId("reserva-1")
                        .agencyUserEmail("agency@example.com")
                        .status("confirmed")
                        .build()))
                .expectError(ConflictException.class)
                .verify();
    }

    private ReservationRequestDetails validDetails() {
        return ReservationRequestDetails.builder()
                .tourPackageId(6L)
                .userEmail("user@example.com")
                .startDate(LocalDate.now().plusDays(5))
                .endDate(LocalDate.now().plusDays(7))
                .travelers(2)
                .customerPhone(null)
                .contactPreference("IN_APP")
                .customerMessage("Deseo confirmar disponibilidad")
                .consentAccepted(true)
                .consentVersion("2026-07-04")
                .build();
    }

    private void assertContactPreference(String input, String expected) {
        StepVerifier.create(useCase.createRequest(validDetails().toBuilder()
                        .contactPreference(input)
                        .build()))
                .assertNext(created -> assertEquals(expected, created.getContactPreference()))
                .verifyComplete();
    }

    private void assertValidationError(ReservationRequestDetails details) {
        StepVerifier.create(useCase.createRequest(details))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    private void assertInvalidPackageDays(Integer days) {
        when(tourPackageRepository.findById(6L)).thenReturn(Mono.just(activePackage().toBuilder().days(days).build()));

        StepVerifier.create(useCase.createRequest(validDetails().toBuilder()
                        .endDate(null)
                        .build()))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    private TourPackage activePackage() {
        return TourPackage.builder()
                .id(6L)
                .agencyId(2L)
                .title("Paquete de pruebas")
                .price(250000L)
                .days(2)
                .isActive(true)
                .build();
    }

    private ReservationDraft reservation(String status) {
        return ReservationDraft.builder()
                .id("reserva-1")
                .userEmail("user@example.com")
                .tourPackageId(6L)
                .agencyId(2L)
                .packageTitle("Paquete de pruebas")
                .totalAmount(BigDecimal.valueOf(250000L))
                .currency("COP")
                .startDate(LocalDate.now().plusDays(5))
                .endDate(LocalDate.now().plusDays(7))
                .travelers(2)
                .status(status)
                .paymentProvider("agency_managed")
                .paymentStatus("pending")
                .build();
    }
}
