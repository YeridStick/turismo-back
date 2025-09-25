package co.turismo.model.visits.gateways;

import co.turismo.model.visits.PlaceVisit;
import co.turismo.model.visits.TopPlace;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

public interface VisitGateway {
    Mono<Integer> computeDistanceIfWithin(Long placeId, double lat, double lng, int radius);

    Mono<PlaceVisit> insertPending(Long placeId, Long userId, String deviceId,
                                   Integer distanceM, Integer accuracyM, String metaJson);

    Mono<PlaceVisit> confirmVisit(Long visitId, Double lat, Double lng, Integer accuracyM, String metaJson);

    Mono<Boolean> existsConfirmedToday(Long placeId, String deviceId);

    Mono<Void> upsertDaily(Long placeId);

    Flux<TopPlace> topPlaces(LocalDate from, LocalDate to, int limit);

    Mono<PlaceVisit> findById(Long visitId);
}
