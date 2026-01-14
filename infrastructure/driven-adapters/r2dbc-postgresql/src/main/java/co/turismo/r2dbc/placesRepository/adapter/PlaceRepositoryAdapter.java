package co.turismo.r2dbc.placesRepository.adapter;

import co.turismo.model.place.CreatePlaceRequest;
import co.turismo.model.place.Place;
import co.turismo.model.place.UpdatePlaceRequest;
import co.turismo.model.place.gateways.PlaceRepository;
import co.turismo.model.user.gateways.UserRepository;
import co.turismo.r2dbc.helper.ReactiveAdapterOperations;
import co.turismo.r2dbc.placesRepository.entity.PlaceData;
import co.turismo.r2dbc.placesRepository.repository.PlaceAdapterRepository;
import lombok.extern.slf4j.Slf4j;
import org.reactivecommons.utils.ObjectMapper;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
@Slf4j
public class PlaceRepositoryAdapter extends ReactiveAdapterOperations<Place, PlaceData, Long, PlaceAdapterRepository>
        implements PlaceRepository {

    private final UserRepository userRepository;

    protected PlaceRepositoryAdapter(
            PlaceAdapterRepository repository,
            ObjectMapper mapper,
            UserRepository userRepository
    ) {
        super(repository, mapper, data -> mapper.map(data, Place.class));
        this.userRepository = userRepository;
    }


    @Override
    public Mono<Place> create(CreatePlaceRequest request) {
        return userRepository.findByEmail(request.getOwnerEmail())
                .switchIfEmpty(Mono.error(new RuntimeException("Owner no encontrado: " + request.getOwnerEmail())))
                .flatMap(user -> {
                    if (user.getId() == null) {
                        return Mono.error(new RuntimeException("Owner encontrado pero con id NULL (revisa mapping de UserRepository)"));
                    }
                    return repository.insertPlace(
                            user.getId(),
                            request.getName(),
                            request.getDescription(),
                            request.getCategoryId(),
                            request.getLat(),
                            request.getLng(),
                            request.getAddress(),
                            request.getPhone(),
                            request.getWebsite(),
                            request.getImageUrls() == null ? new String[0] : request.getImageUrls()
                    );
                })
                .map(this::toEntity);
    }

    @Override
    public Flux<Place> findNearby(double lat, double lng, double radiusMeters, int limit, Long categoryId) {
        return repository.findNearby(lat, lng, radiusMeters, limit, categoryId)
                .map(this::toEntity);
    }

    @Override
    public Flux<Place> findAllPlace() {
        return super.findAll()
                .doOnNext(p -> log.info("place={}", p.getName()));
    }

    @Override
    public Flux<Place> search(String q, Long categoryId, boolean onlyNearby,
                              Double lat, Double lng, Double radiusMeters, int page, int size) {
        int limit  = Math.max(1, size);
        int offset = Math.max(0, page) * limit;
        return repository.search(q, categoryId, onlyNearby, lat, lng, radiusMeters, limit, offset)
                .map(this::toEntity);
    }

    @Override
    public Mono<Place> patch(long id, UpdatePlaceRequest req) {
        return repository.patchPlace(
                        id,
                        req.getName(),
                        req.getDescription(),
                        req.getCategoryId(),
                        req.getLat(),
                        req.getLng(),
                        req.getAddress(),
                        req.getPhone(),
                        req.getWebsite(),
                        req.getImageUrls()
                )
                .switchIfEmpty(Mono.error(new IllegalStateException("Lugar no encontrado")))
                .map(this::toEntity);
    }

    @Override
    public Mono<Place> verifyPlace(long id, boolean verified, boolean active, long adminId) {
        return repository.verifyPlace(id, verified, active, adminId)
                .map(this::toEntity);
    }

    @Override
    public Mono<Place> setActive(long id, boolean active) {
        return repository.setActive(id, active)
                .map(this::toEntity);
    }

    @Override
    public Mono<Place> setActiveIfOwner(String ownerEmail, long placeId, boolean active) {
        return userRepository.findByEmail(ownerEmail)
                .switchIfEmpty(Mono.error(new RuntimeException("Owner no encontrado")))
                .flatMap(owner -> repository.setActiveIfOwner(placeId, active, owner.getId()))
                .switchIfEmpty(Mono.error(new RuntimeException("No autorizado para este lugar")))
                .map(this::toEntity);
    }

    @Override
    public Flux<Place> findPlacesByOwnerEmail(String ownerEmail) {
        return userRepository.findByEmail(ownerEmail)
                .switchIfEmpty(Mono.error(new RuntimeException("Usuario no encontrado")))
                .flatMapMany(u -> repository.findByOwnerId(u.getId()))
                .map(this::toEntity);
    }

    @Override
    public Flux<Place> findByIds(Long[] ids) {
        if (ids == null || ids.length == 0) {
            return Flux.empty();
        }
        return repository.findByIds(ids)
                .map(this::toEntity);
    }

    @Override
    public Mono<Void> addOwnerToPlace(String ownerEmailToAdd, long placeId) {
        return Mono.error(new UnsupportedOperationException("Co‑owners no soportados en la nueva lógica"));
    }

    @Override
    public Mono<Void> removeOwnerFromPlace(String ownerEmailToRemove, long placeId) {
        return Mono.error(new UnsupportedOperationException("Co‑owners no soportados en la nueva lógica"));
    }

    @Override
    public Mono<Place> findByPlaces(Long id) {
        return findById(id);
    }

    @Override
    public Mono<Place> deletePalce(Long id) {
        return findById(id)
                .switchIfEmpty(Mono.error(new RuntimeException("Palce no encontrado")))
                .flatMap(place ->  deleteById(id).thenReturn(place));
    }
}
