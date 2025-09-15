package co.turismo.usecase.visit;

import co.turismo.model.visits.TopPlace;
import co.turismo.model.visits.VisitStatus;
import co.turismo.model.visits.gateways.VisitGateway;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

@RequiredArgsConstructor
public class VisitsUseCase {

    private final VisitGateway gateway;

    // Podrías inyectar estos desde configuración
    private final int RADIUS_M = 80;
    private final int MIN_STAY_SECONDS = 180;
    private final int MAX_ACCURACY_M = 75;

    /* ---------- CHECKIN ---------- */
    public record CheckinCmd(Long placeId, double lat, double lng,
                             Integer accuracyM, String deviceId, String metaJson,
                             Long userId) {}
    public record CheckinRes(Long visitId, String status, int minStaySeconds, int distanceM) {}

    public Mono<CheckinRes> checkin(CheckinCmd cmd) {
        if (cmd.accuracyM() != null && cmd.accuracyM() > MAX_ACCURACY_M) {
            return Mono.error(new IllegalArgumentException("GPS con baja precisión"));
        }
        return gateway.computeDistanceIfWithin(cmd.placeId(), cmd.lat(), cmd.lng(), RADIUS_M)
                .switchIfEmpty(Mono.error(new IllegalStateException("Fuera del radio permitido")))
                .flatMap(distance ->
                        gateway.insertPending(
                                cmd.placeId(), cmd.userId(), cmd.deviceId(),
                                distance, cmd.accuracyM(), cmd.metaJson() != null ? cmd.metaJson() : "{}"
                        ).map(v -> new CheckinRes(v.getId(), v.getStatus().name(), MIN_STAY_SECONDS, distance))
                );
    }

    /* ---------- CONFIRM ---------- */
    public record ConfirmCmd(Long visitId, double lat, double lng, Integer accuracyM) {}
    public record ConfirmRes(String status, Instant confirmedAt) {}

    public Mono<ConfirmRes> confirm(ConfirmCmd cmd) {
        return gateway.findById(cmd.visitId())
                .switchIfEmpty(Mono.error(new IllegalStateException("Visita no encontrada")))
                .flatMap(v -> {
                    if (v.getStatus() != VisitStatus.pending) {
                        return Mono.error(new IllegalStateException("Visita ya gestionada"));
                    }
                    if (v.getStartedAt() == null ||
                            Duration.between(v.getStartedAt(), Instant.now()).getSeconds() < MIN_STAY_SECONDS) {
                        return Mono.error(new IllegalStateException("Aún no cumples permanencia mínima"));
                    }
                    return gateway.computeDistanceIfWithin(v.getPlaceId(), cmd.lat(), cmd.lng(), RADIUS_M)
                            .switchIfEmpty(Mono.error(new IllegalStateException("Ya no estás cerca del sitio")))
                            .flatMap(d -> gateway.existsConfirmedToday(v.getPlaceId(), v.getDeviceId())
                                    .flatMap(already -> {
                                        if (Boolean.TRUE.equals(already)) {
                                            return Mono.error(new IllegalStateException("Ya registraste visita hoy"));
                                        }
                                        return gateway.confirmVisit(cmd.visitId())
                                                .flatMap(ok -> gateway.upsertDaily(v.getPlaceId()).thenReturn(ok));
                                    }))
                            .map(ok -> new ConfirmRes(ok.getStatus().name(), ok.getConfirmedAt()));
                });
    }

    /* ---------- TOP ---------- */
    public Flux<TopPlace> top(LocalDate from, LocalDate to, int limit) {
        return gateway.topPlaces(from, to, limit);
    }
}