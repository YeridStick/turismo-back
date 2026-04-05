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
        Integer limit = 100;
        Integer offset = 0;

        return agencyRepository.findByUserEmail(userEmail)
                .switchIfEmpty(Mono.error(new IllegalStateException("Agencia no encontrada para el usuario")))
                .flatMap(agency -> validatePlaces(placeIds, limit, offset)
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
                        }))
                .flatMap(tourPackage -> attachPlaces(tourPackage, limit, offset));
    }

    public Flux<TourPackage> findAll(Integer limit, Integer offset) {
        return tourPackageRepository.findAll(limit, offset)
                .concatMap(tourPackage -> attachPlaces(tourPackage, 100, 0));
    }

    public Mono<TourPackage> findById(long id, Integer limit, Integer offset) {
        return tourPackageRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalStateException("Paquete no encontrado")))
                .flatMap(tourPackage -> attachPlaces(tourPackage, 100, 0));
    }

    public Mono<TourPackage> update(String userEmail, Long packageId, co.turismo.model.tourpackage.UpdateTourPackageRequest request) {
        if (request == null) {
            return Mono.error(new IllegalArgumentException("Body requerido"));
        }
        return tourPackageRepository.findById(packageId)
                .switchIfEmpty(Mono.error(new IllegalStateException("Paquete no encontrado")))
                .flatMap(tourPackage -> agencyRepository.findByUserEmail(userEmail)
                        .switchIfEmpty(Mono.error(new IllegalStateException("Agencia no encontrada para el usuario")))
                        .flatMap(agency -> {
                            if (!agency.getId().equals(tourPackage.getAgencyId())) {
                                return Mono.error(new IllegalStateException("No tienes permisos para editar este paquete"));
                            }
                            
                            Long[] placeIds = normalizePlaceIds(request.getPlaceIds());
                            if (request.getPlaceIds() != null && placeIds.length == 0) {
                                return Mono.error(new IllegalArgumentException("placeIds no puede estar vacío si se envía"));
                            }
                            
                            Mono<Void> validationMono = Mono.empty();
                            if (request.getPlaceIds() != null) {
                                validationMono = validatePlaces(placeIds, 100, 0);
                            }
                            
                            return validationMono
                                    .then(tourPackageRepository.update(packageId, request.toBuilder().placeIds(request.getPlaceIds() != null ? placeIds : null).build()))
                                    .flatMap(updated -> attachPlaces(updated, 100, 0));
                        }));
    }

    public Mono<Void> delete(String userEmail, Long packageId) {
        return tourPackageRepository.findById(packageId)
                .switchIfEmpty(Mono.error(new IllegalStateException("Paquete no encontrado")))
                .flatMap(tourPackage -> agencyRepository.findByUserEmail(userEmail)
                        .switchIfEmpty(Mono.error(new IllegalStateException("Agencia no encontrada para el usuario")))
                        .flatMap(agency -> {
                            if (!agency.getId().equals(tourPackage.getAgencyId())) {
                                return Mono.error(new IllegalStateException("No tienes permisos para eliminar este paquete"));
                            }
                            return tourPackageRepository.delete(packageId);
                        }));
    }

    private Mono<Void> validatePlaces(Long[] placeIds, Integer limit, Integer offset) {
        return placeRepository.findByIds(placeIds, limit, offset) // Limitar cantidad de sitios por Paquete
                .collectList()
                .flatMap(found -> {
                    if (found.size() != placeIds.length) {
                        return Mono.error(new IllegalArgumentException("Uno o más lugares no existen"));
                    }
                    return Mono.empty();
                });
    }

    private Mono<TourPackage> attachPlaces(TourPackage tourPackage, Integer limit, Integer offset) {
        Long[] placeIds = tourPackage.getPlaceIds();
        return placeRepository.findByIds(placeIds, limit, offset)
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
