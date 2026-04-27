package co.turismo.usecase.place;

import co.turismo.model.error.NotFoundException;
import co.turismo.model.place.CreatePlaceRequest;
import co.turismo.model.place.Place;
import co.turismo.model.place.strategy.PlaceSearchCriteria;
import co.turismo.model.place.UpdatePlaceRequest;
import co.turismo.model.place.gateways.PlaceCachePort;
import co.turismo.model.place.gateways.PlaceRepository;
import co.turismo.model.place.strategy.PlaceSearchFactoryGateway;
import co.turismo.model.userIdentityPort.UserIdentityPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.file.AccessDeniedException;

@RequiredArgsConstructor
public class PlaceUseCase {

    private final PlaceRepository placeRepository;
    private final UserIdentityPort userIdentityPort;
    private final PlaceSearchFactoryGateway placeSearchFactory;
    private final PlaceCachePort placeCachePort;

    public Mono<Place> createPlace(CreatePlaceRequest cmd) {
        return placeRepository.create(cmd)
                .flatMap(place -> placeCachePort.invalidateSearchCache()
                        .thenReturn(place)
                        .onErrorResume(e -> Mono.just(place)));
    }

    public Flux<Place> searchPlace(PlaceSearchCriteria placeSearchCriteria) {
        return placeCachePort.getSearchResults(placeSearchCriteria)
                .flatMapMany(cachedPlaces -> {
                    if (cachedPlaces != null && !cachedPlaces.isEmpty()) {
                        return Flux.fromIterable(cachedPlaces);
                    }
                    return Flux.empty();
                })
                .switchIfEmpty(
                        placeSearchFactory.getStrategy(placeSearchCriteria.getMode())
                                .execute(placeSearchCriteria)
                                .collectList()
                                .flatMapMany(places ->
                                        placeCachePort.saveSearchResults(placeSearchCriteria, places)
                                                .thenReturn(places)
                                                .flatMapMany(Flux::fromIterable)
                                                .onErrorResume(e -> Flux.fromIterable(places))
                                )
                )
                .onErrorResume(e ->
                        placeSearchFactory.getStrategy(placeSearchCriteria.getMode())
                                .execute(placeSearchCriteria)
                );
    }

    public Mono<Place> verifyPlaceByAdmin(String adminEmail, long placeId, boolean approve) {
        return userIdentityPort.getUserIdForEmail(adminEmail)
                .switchIfEmpty(Mono.error(new NotFoundException("Admin no encontrado")))
                .flatMap(admin -> placeRepository.verifyPlace(placeId, approve, approve, admin.id()))
                .flatMap(place -> placeCachePort.invalidateSearchCache()
                        .thenReturn(place)
                        .onErrorResume(e -> Mono.just(place)));
    }

    public Mono<Place> setActiveByOwner(String ownerEmail, long placeId, boolean active) {
        return placeRepository.setActiveIfOwner(ownerEmail, placeId, active)
                .flatMap(place -> placeCachePort.invalidateSearchCache()
                        .thenReturn(place)
                        .onErrorResume(e -> Mono.just(place)));
    }

    public Flux<Place> findMine(String ownerEmail, Integer Limit, Integer Offset) {
        return placeRepository.findPlacesByOwnerEmail(ownerEmail, Limit, Offset);
    }

    public Mono<Place> patch(long id, UpdatePlaceRequest req) {
        return placeRepository.patch(id, req)
                .flatMap(place -> placeCachePort.invalidateSearchCache()
                        .thenReturn(place)
                        .onErrorResume(e -> Mono.just(place)));
    }

    public Mono<Place> findByIdPlace(long id) {
        return placeRepository.findByPlaces(id)
                .switchIfEmpty(Mono.error(new NotFoundException("Place no encontrado")));
    }

    public Mono<Place> setActive(long placeId, boolean active) {
        return placeRepository.setActive(placeId, active)
                .flatMap(place -> placeCachePort.invalidateSearchCache()
                        .thenReturn(place)
                        .onErrorResume(e -> Mono.just(place)));
    }

    public Mono<Place> deleteByOwnerOrAdmin(String email, long placeId) {
        return userIdentityPort.getUserIdForEmail(email)
            .switchIfEmpty(Mono.error(new NotFoundException("Usuario no encontrado")))
            .flatMap(user ->
                placeRepository.findByPlaces(placeId)
                    .switchIfEmpty(Mono.error(new NotFoundException("Place no encontrado")))
                    .flatMap(place -> {
                        boolean isOwner = place.getOwnerUserId() != null
                                && place.getOwnerUserId().equals(user.id());

                        if (!isOwner) {
                            return Mono.error(new AccessDeniedException("No tienes permiso para eliminar este sitio"));
                        }
                        return placeRepository.deletePalce(placeId)
                                .flatMap(deleted -> placeCachePort.invalidateSearchCache()
                                        .thenReturn(deleted)
                                        .onErrorResume(e -> Mono.just(deleted)));
                    })
            );
    }
}
