package co.turismo.r2dbc.placesRepository.adapter;

import co.turismo.model.place.Place;
import co.turismo.model.place.gateways.PlaceRepository;
import co.turismo.r2dbc.placesRepository.entity.PlaceData;
import co.turismo.r2dbc.placesRepository.repository.PlaceAdapterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class PlaceRepositoryAdapter implements PlaceRepository {

    private final PlaceAdapterRepository repo;

    @Override
    public Mono<Place> create(Place place, double lat, double lng) {
        return repo.insertPlace(
                place.getOwnerUserId(), place.getName(), place.getDescription(), place.getCategory(),
                lat, lng, place.getAddress(), place.getPhone(), place.getWebsite()
        ).map(this::toDomain);
    }

    @Override
    public Flux<Place> findNearby(double lat, double lng, double radiusMeters, int limit) {
        return repo.findNearby(lat, lng, radiusMeters, limit).map(this::toDomain);
    }

    @Override
    public Mono<Place> verifyPlace(long id, boolean verified, boolean active, long adminId) {
        return repo.verifyPlace(id, verified, active, adminId).map(this::toDomain);
    }


    public Mono<Place> setActive(long id, boolean active) {
        return repo.setActive(id, active).map(this::toDomain);
    }

    private Place toDomain(PlaceData r) {
        return Place.builder()
                .id(r.getId())
                .ownerUserId(r.getOwnerUserId())
                .name(r.getName())
                .description(r.getDescription())
                .category(r.getCategory())
                .lat(r.getLat())
                .lng(r.getLng())
                .address(r.getAddress())
                .phone(r.getPhone())
                .website(r.getWebsite())
                .verified(r.getIsVerified())
                .active(r.getIsActive())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
