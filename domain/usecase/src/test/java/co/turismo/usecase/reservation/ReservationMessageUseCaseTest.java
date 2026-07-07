package co.turismo.usecase.reservation;

import co.turismo.model.agency.gateways.AgencyRepository;
import co.turismo.model.notification.gateways.AppNotificationGateway;
import co.turismo.model.reservation.ReservationDraft;
import co.turismo.model.reservation.ReservationMessage;
import co.turismo.model.reservation.gateways.ReservationGateway;
import co.turismo.model.reservation.gateways.ReservationMessageGateway;
import co.turismo.model.user.gateways.UserRepository;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationMessageUseCaseTest {

    @Mock
    private ReservationGateway reservationGateway;
    @Mock
    private ReservationMessageGateway reservationMessageGateway;
    @Mock
    private AgencyRepository agencyRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AppNotificationGateway appNotificationGateway;

    private ReservationMessageUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ReservationMessageUseCase(
                reservationGateway,
                reservationMessageGateway,
                agencyRepository,
                userRepository,
                appNotificationGateway
        );
        lenient().when(appNotificationGateway.save(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        lenient().when(reservationMessageGateway.save(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
    }

    @Test
    void agencyMessageShouldAutomaticallyMarkRequestedReservationAsContacted() {
        ReservationDraft requested = reservation("requested");
        ReservationDraft contacted = reservation("contacted");

        when(reservationGateway.findByIdForAdmin("reserva-1")).thenReturn(Mono.just(requested));
        when(reservationGateway.markContactedByAgencyReply("reserva-1", 2L)).thenReturn(Mono.just(contacted));

        StepVerifier.create(useCase.sendFromAgency(
                        "admin@example.com",
                        true,
                        "reserva-1",
                        "Hola, ya estamos revisando tu solicitud."))
                .assertNext(message -> {
                    assertEquals("AGENCY", message.getSenderType());
                    assertEquals("Hola, ya estamos revisando tu solicitud.", message.getBody());
                })
                .verifyComplete();

        ArgumentCaptor<ReservationMessage> messageCaptor = ArgumentCaptor.forClass(ReservationMessage.class);
        verify(reservationMessageGateway, times(2)).save(messageCaptor.capture());
        assertTrue(messageCaptor.getAllValues().stream()
                .anyMatch(message -> "SYSTEM".equals(message.getSenderType())
                        && message.getBody().contains("La agencia tomó tu solicitud")));
        assertTrue(messageCaptor.getAllValues().stream()
                .anyMatch(message -> "AGENCY".equals(message.getSenderType())
                        && "Hola, ya estamos revisando tu solicitud.".equals(message.getBody())));
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
                .endDate(LocalDate.now().plusDays(6))
                .status(status)
                .travelers(2)
                .contactPreference("IN_APP")
                .paymentProvider("agency_managed")
                .paymentStatus("pending")
                .consentAccepted(true)
                .consentVersion("2026-07")
                .createdAt(OffsetDateTime.now().minusMinutes(5))
                .build();
    }
}
