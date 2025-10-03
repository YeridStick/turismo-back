package co.turismo.usecase.visit;

import co.turismo.model.user.User;
import co.turismo.model.user.gateways.UserRepository;
import co.turismo.model.visits.PlaceBriefUC;
import co.turismo.model.visits.PlaceNearby;
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
    private final UserRepository userRepository;

    // Podrías inyectar estos desde configuración
    private final int RADIUS_M = 80;
    private final int MIN_STAY_SECONDS = 180;
    private final int MAX_ACCURACY_M = 75;

    /* ---------- CHECKIN ---------- */
    // Cambiamos userId -> email
    public record CheckinCmd(Long placeId, double lat, double lng,
                             Integer accuracyM, String deviceId, String metaJson,
                             String email) {}
    public record CheckinRes(Long visitId, String status, int minStaySeconds, int distanceM) {}

    public Mono<CheckinRes> checkin(CheckinCmd cmd) {
        if (cmd.accuracyM() != null && cmd.accuracyM() > MAX_ACCURACY_M) {
            return Mono.error(new IllegalArgumentException("GPS con baja precisión"));
        }

        // 1) Resolver userId por email
        Mono<Long> userIdMono = Mono.justOrEmpty(cmd.email())
                .filter(e -> !e.isBlank())
                .flatMap(email ->
                        userRepository.findByEmail(email)
                                .switchIfEmpty(Mono.error(new IllegalArgumentException("Usuario no encontrado con email: " + email)))
                                .map(User::getId)
                );

        // 2) Validar distancia y crear pending
        return gateway.computeDistanceIfWithin(cmd.placeId(), cmd.lat(), cmd.lng(), RADIUS_M)
                .switchIfEmpty(Mono.error(new IllegalStateException("Fuera del radio permitido")))
                .zipWith(userIdMono)
                .flatMap(tuple -> {
                    int distance = tuple.getT1();
                    Long userId  = tuple.getT2();

                    return gateway.insertPending(
                                    cmd.placeId(), userId, cmd.deviceId(),
                                    distance, cmd.accuracyM(),
                                    cmd.metaJson() != null ? cmd.metaJson() : "{}"
                            )
                            .map(v -> new CheckinRes(v.getId(), v.getStatus().name(), MIN_STAY_SECONDS, distance));
                });
    }

    /* ---------- CONFIRM ---------- */
    public record ConfirmCmd(Long visitId, double lat, double lng, Integer accuracyM) {}
    public record ConfirmRes(String status, Instant confirmedAt, PlaceBriefUC place) {}

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
                            // Usa userId si existe; si no, deviceId
                            .flatMap(d -> gateway.existsConfirmedToday(v.getPlaceId(), v.getUserId(), v.getDeviceId())
                                    .flatMap(already -> {
                                        if (Boolean.TRUE.equals(already)) {
                                            return Mono.error(new IllegalStateException("Ya registraste visita hoy"));
                                        }
                                        return gateway.confirmVisit(cmd.visitId(), cmd.lat(), cmd.lng(), cmd.accuracyM(), null)
                                                .flatMap(ok -> gateway.getPlaceBrief(v.getPlaceId())
                                                        .flatMap(pb -> gateway.upsertDaily(v.getPlaceId()).thenReturn(
                                                                new ConfirmRes(ok.getStatus().name(), ok.getConfirmedAt(), pb)
                                                        )));
                                    })
                            );
                });
    }

    public Flux<PlaceNearby> nearby(double lat, double lng, int radius, int limit) {
        return gateway.findNearby(lat, lng, radius, limit);
    }

    public Flux<TopPlace> top(LocalDate from, LocalDate to, int limit) {
        return gateway.topPlaces(from, to, limit);
    }
}
