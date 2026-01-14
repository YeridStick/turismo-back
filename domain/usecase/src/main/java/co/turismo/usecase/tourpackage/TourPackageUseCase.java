package co.turismo.usecase.tourpackage;

import co.turismo.model.agency.gateways.AgencyRepository;
import co.turismo.model.place.Place;
import co.turismo.model.place.gateways.PlaceRepository;
import co.turismo.model.tourpackage.CreateTourPackageRequest;
import co.turismo.model.tourpackage.TourPackage;
import co.turismo.model.tourpackage.gateways.TourPackageRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

@RequiredArgsConstructor
public class TourPackageUseCase {

    private final TourPackageRepository tourPackageRepository;
    private final AgencyRepository agencyRepository;
    private final PlaceRepository placeRepository;

    public Mono<TourPackage> create(String userEmail, CreateTourPackageRequest request) {
        if (request == null) {
            return Mono.error(new IllegalArgumentException("Body requerido"));
        }
        if (request.getTitle() == null || request.getTitle().isBlank()) {
            return Mono.error(new IllegalArgumentException("title es obligatorio"));
        }
        if (request.getDescription() == null || request.getDescription().isBlank()) {
            return Mono.error(new IllegalArgumentException("description es obligatorio"));
        }
        if (request.getPrice() == null || request.getPrice() <= 0) {
            return Mono.error(new IllegalArgumentException("price es obligatorio"));
        }

        Long[] placeIds = normalizePlaceIds(request.getPlaceIds());
        if (placeIds.length == 0) {
            return Mono.error(new IllegalArgumentException("placeIds es obligatorio"));
        }

        return agencyRepository.findByUserEmail(userEmail)
                .switchIfEmpty(Mono.error(new IllegalStateException("Agencia no encontrada para el usuario")))
                .flatMap(agency -> validatePlaces(placeIds)
                        .then(tourPackageRepository.create(request.toBuilder()
                                .agencyId(agency.getId())
                                .placeIds(placeIds)
                                .build()))
                        .flatMap(created -> {
                            if (created.getId() == null) {
                                return Mono.just(created);
                            }
                            return tourPackageRepository.findById(created.getId())
                                    .defaultIfEmpty(created);
                        })
                )
                .flatMap(this::attachPlaces);
    }

    public Flux<TourPackage> findAll() {
        return tourPackageRepository.findAll()
                .concatMap(this::attachPlaces);
    }

    public Mono<TourPackage> findById(long id) {
        return tourPackageRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalStateException("Paquete no encontrado")))
                .flatMap(this::attachPlaces);
    }

    private Mono<Void> validatePlaces(Long[] placeIds) {
        return placeRepository.findByIds(placeIds)
                .collectList()
                .flatMap(found -> {
                    if (found.size() != placeIds.length) {
                        return Mono.error(new IllegalArgumentException("Uno o más lugares no existen"));
                    }
                    return Mono.empty();
                });
    }

    private Mono<TourPackage> attachPlaces(TourPackage tourPackage) {
        Long[] placeIds = tourPackage.getPlaceIds();
        if (placeIds == null || placeIds.length == 0) {
            return Mono.just(tourPackage.toBuilder().places(List.of()).build());
        }
        return placeRepository.findByIds(placeIds)
                .collectList()
                .map(places -> tourPackage.toBuilder().places(places).build());
    }

    private static Long[] normalizePlaceIds(Long[] placeIds) {
        if (placeIds == null || placeIds.length == 0) {
            return new Long[0];
        }
        var set = new LinkedHashSet<Long>();
        for (Long id : placeIds) {
            if (id != null && id > 0) {
                set.add(id);
            }
        }
        return set.stream().filter(Objects::nonNull).toArray(Long[]::new);
    }
}
