package co.turismo.r2dbc.visitsRepository.adapter;

import co.turismo.model.visits.*;
import co.turismo.model.visits.gateways.VisitGateway;
import co.turismo.r2dbc.helper.ReactiveAdapterOperations;
import co.turismo.r2dbc.visitsRepository.entity.PlaceVisitData;
import co.turismo.r2dbc.visitsRepository.repository.VisitRepository;
import io.r2dbc.postgresql.codec.Json;
import org.reactivecommons.utils.ObjectMapper;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

/**
 * Adapter que implementa el Gateway de dominio usando R2DBC.
 * OJO: los genéricos de ReactiveAdapterOperations deben ser:
 * <Domain, Data, ID, Repository>
 */
@Component
public class VisitRepositoryAdapter
        extends ReactiveAdapterOperations<PlaceVisit, PlaceVisitData, Long, VisitRepository>
        implements VisitGateway {

    /**
     * En el scaffold, el constructor típico pasa:
     *  - repository
     *  - mapper
     *  - función de mapeo Data -> Domain
     */
    public VisitRepositoryAdapter(VisitRepository repository, ObjectMapper mapper) {
        super(repository, mapper, VisitRepositoryAdapter::toDomain);
    }

    /* =======================
     *     Mapeadores
     * ======================= */
    private static PlaceVisit toDomain(PlaceVisitData d) {
        return PlaceVisit.builder()
                .id(d.getId())
                .placeId(d.getPlaceId())
                .userId(d.getUserId())
                .deviceId(d.getDeviceId())
                .startedAt(d.getStartedAt())
                .confirmedAt(d.getConfirmedAt())
                .status(VisitStatus.valueOf(d.getStatus()))   // "pending|confirmed|rejected"
                .distanceM(d.getDistanceM())
                .accuracyM(d.getAccuracyM())
                .metaJson(d.getMeta() != null ? d.getMeta().asString() : "{}")
                .build();
    }

    private static Json toJson(String json) {
        return json == null ? Json.of("{}") : Json.of(json);
    }

    /* =======================
     *   Implementación Gateway
     * ======================= */

    @Override
    public Mono<PlaceBriefUC> getPlaceBrief(Long placeId) {
        return repository.getPlaceBrief(placeId)
                .map(r -> new PlaceBriefUC(
                        r.getId(), r.getName(), r.getAddress(), r.getDescription(),
                        r.getCategoryId(), r.getLat(), r.getLng(),
                        r.getImageUrls() != null ? r.getImageUrls() : java.util.List.of()
                ));
    }

    @Override
    public Mono<Integer> computeDistanceIfWithin(Long placeId, double lat, double lng, int radius) {
        return repository.computeDistanceIfWithin(placeId, lat, lng, radius);
    }

    @Override
    public Mono<PlaceVisit> insertPending(Long placeId, Long userId, String deviceId,
                                          Integer distanceM, Integer accuracyM, String metaJson) {
        return repository
                .insertPending(placeId, userId, deviceId, distanceM, accuracyM, metaJson)
                .map(VisitRepositoryAdapter::toDomain);
    }

    @Override
    public Mono<PlaceVisit> confirmVisit(Long visitId, Double lat, Double lng, Integer accuracyM, String metaJson) {
        return repository.confirmVisit(visitId, lat, lng, accuracyM, metaJson)
                .map(VisitRepositoryAdapter::toDomain);
    }


    @Override
    public Mono<Boolean> existsConfirmedToday(Long placeId, Long userId, String deviceId) {
        return repository.existsConfirmedToday(placeId, userId, deviceId)
                .map(x -> true)
                .defaultIfEmpty(false);
    }

    @Override
    public Mono<Void> upsertDaily(Long placeId) {
        return repository.upsertDaily(placeId);
    }

    @Override
    public Flux<TopPlace> topPlaces(LocalDate from, LocalDate to, int limit) {
        return repository.topPlaces(from, to, limit)
                .map(r -> new TopPlace(r.getPlaceId(), r.getName(), r.getVisits()));
    }

    @Override
    public Mono<PlaceVisit> findById(Long visitId) {
        return repository.findById(visitId).map(VisitRepositoryAdapter::toDomain);
    }

    @Override
    public Flux<PlaceNearby> findNearby(double lat, double lng, int radius, int limit) {
        return repository.findNearby(lat, lng, radius, limit)
                .map(r -> new co.turismo.model.visits.PlaceNearby(
                        r.getId(), r.getName(),
                        r.getLat(), r.getLng(),
                        r.getAddress(), r.getDescription(),
                        r.getCategoryId(),
                        r.getImageUrls() != null ? r.getImageUrls() : java.util.List.of(),
                        r.getDistanceM()
                ));
    }
}