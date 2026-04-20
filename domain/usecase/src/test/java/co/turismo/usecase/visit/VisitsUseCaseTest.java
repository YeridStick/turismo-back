package co.turismo.usecase.visit;

import co.turismo.model.userIdentityPort.UserIdentityPort;
import co.turismo.model.userIdentityPort.UserSummary;
import co.turismo.model.visits.PlaceBriefUC;
import co.turismo.model.visits.PlaceVisit;
import co.turismo.model.visits.VisitStatus;
import co.turismo.model.visits.gateways.VisitGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VisitsUseCaseTest {

    @Mock
    private VisitGateway gateway;
    @Mock
    private UserIdentityPort userIdentityPort;

    private VisitsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new VisitsUseCase(gateway, userIdentityPort);
    }

    @Test
    void checkinShouldFailWhenGpsAccuracyIsTooLow() {
        VisitsUseCase.CheckinCmd cmd = new VisitsUseCase.CheckinCmd(
                1L, 2.0, 3.0, 100, "device-1", "{}", "ana@example.com"
        );

        StepVerifier.create(useCase.checkin(cmd))
                .expectErrorMatches(error -> error.getMessage().contains("GPS"))
                .verify();
    }

    @Test
    void checkinShouldInsertPendingVisitWhenDataIsValid() {
        VisitsUseCase.CheckinCmd cmd = new VisitsUseCase.CheckinCmd(
                1L, 2.0, 3.0, 20, "device-1", "{}", "ana@example.com"
        );

        PlaceVisit pending = PlaceVisit.builder()
                .id(55L)
                .status(VisitStatus.pending)
                .build();

        when(userIdentityPort.getUserIdForEmail("ana@example.com"))
                .thenReturn(Mono.just(new UserSummary(9L, "ana@example.com")));
        when(gateway.computeDistanceIfWithin(1L, 2.0, 3.0, 80)).thenReturn(Mono.just(30));
        when(gateway.insertPending(1L, 9L, "device-1", 30, 20, "{}")).thenReturn(Mono.just(pending));

        StepVerifier.create(useCase.checkin(cmd))
                .assertNext(res -> {
                    assertEquals(55L, res.visitId());
                    assertEquals("pending", res.status());
                    assertEquals(180, res.minStaySeconds());
                    assertEquals(30, res.distanceM());
                })
                .verifyComplete();
    }

    @Test
    void confirmShouldFailWhenVisitIsAlreadyManaged() {
        PlaceVisit visit = PlaceVisit.builder()
                .id(5L)
                .placeId(1L)
                .status(VisitStatus.confirmed)
                .startedAt(Instant.now().minusSeconds(500))
                .build();

        when(gateway.findById(5L)).thenReturn(Mono.just(visit));

        StepVerifier.create(useCase.confirm(new VisitsUseCase.ConfirmCmd(5L, 2.0, 3.0, 20)))
                .expectErrorMatches(error -> error.getMessage().contains("gestionada"))
                .verify();
    }

    @Test
    void confirmShouldCompleteSuccessfullyWhenBusinessRulesPass() {
        PlaceVisit pending = PlaceVisit.builder()
                .id(5L)
                .placeId(1L)
                .userId(9L)
                .deviceId("device-1")
                .status(VisitStatus.pending)
                .startedAt(Instant.now().minusSeconds(300))
                .build();

        PlaceVisit confirmed = pending.toBuilder()
                .status(VisitStatus.confirmed)
                .confirmedAt(Instant.now())
                .build();

        PlaceBriefUC brief = new PlaceBriefUC(
                1L, "Parque", "Calle 1", "Desc", 10, 2.0, 3.0, List.of("img")
        );

        when(gateway.findById(5L)).thenReturn(Mono.just(pending));
        when(gateway.computeDistanceIfWithin(1L, 2.0, 3.0, 80)).thenReturn(Mono.just(20));
        when(gateway.existsConfirmedToday(1L, 9L, "device-1")).thenReturn(Mono.just(false));
        when(gateway.confirmVisit(5L, 2.0, 3.0, 20, null)).thenReturn(Mono.just(confirmed));
        when(gateway.getPlaceBrief(1L)).thenReturn(Mono.just(brief));
        when(gateway.upsertDaily(1L)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.confirm(new VisitsUseCase.ConfirmCmd(5L, 2.0, 3.0, 20)))
                .assertNext(res -> {
                    assertEquals("confirmed", res.status());
                    assertEquals(1L, res.place().id());
                })
                .verifyComplete();

        verify(gateway).upsertDaily(1L);
    }
}

