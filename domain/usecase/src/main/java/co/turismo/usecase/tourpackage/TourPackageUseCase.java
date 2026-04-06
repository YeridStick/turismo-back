package co.turismo.usecase.tourpackage;

import co.turismo.model.agency.gateways.AgencyRepository;
import co.turismo.model.auditLog.AuditLog;
import co.turismo.model.auditLog.gateways.AuditLogRepository;
import co.turismo.model.error.ConflictException;
import co.turismo.model.error.NotFoundException;
import co.turismo.model.place.gateways.PlaceRepository;
import co.turismo.model.tourpackage.CreateTourPackageRequest;
import co.turismo.model.tourpackage.UpdateTourPackageRequest;
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
    private final AuditLogRepository auditLogRepository;

    private static final int DEFAULT_PLACE_LIMIT = 100;
    private static final int DEFAULT_PLACE_OFFSET = 0;

    public Mono<TourPackage> create(String userEmail, CreateTourPackageRequest request) {
        Long[] placeIds = normalizePlaceIds(request.getPlaceIds());

        return agencyRepository.findByEmail(userEmail)
                .switchIfEmpty(Mono.error(new NotFoundException("Agencia no encontrada para el email")))
                .flatMap(agency ->
                        validatePlacesExist(placeIds)
                                .then(tourPackageRepository.create(
                                        request.toBuilder()
                                                .agencyId(agency.getId())
                                                .placeIds(placeIds)
                                                .build()))
                )
                .flatMap(created -> resolveFullPackage(created))
                .flatMap(this::attachPlaces);
    }

    public Flux<TourPackage> findAll(Integer limit, Integer offset) {
        return tourPackageRepository.findAll(limit, offset)
                .concatMap(this::attachPlaces);
    }

    /**
     * Lista todos los paquetes turísticos de una agencia específica, incluyendo sus lugares.
     */
    public Flux<TourPackage> findByAgencyId(Long agencyId) {
        return tourPackageRepository.findByAgencyId(agencyId)
                .concatMap(this::attachPlaces);
    }

    public Mono<TourPackage> findById(long id, Integer limit, Integer offset) {
        return tourPackageRepository.findById(id)
                .switchIfEmpty(Mono.error(new NotFoundException("Paquete no encontrado")))
                .flatMap(this::attachPlaces);
    }

    public Mono<TourPackage> update(Long packageId, UpdateTourPackageRequest request) {
        Long[] placeIds = normalizePlaceIds(request.getPlaceIds());

        Mono<Void> placesValidation = (request.getPlaceIds() != null)
                ? validatePlacesExist(placeIds)
                : Mono.empty();

        return placesValidation
                .then(tourPackageRepository.update(packageId,
                        request.toBuilder()
                                .placeIds(request.getPlaceIds() != null ? placeIds : null)
                                .build()))
                .flatMap(this::attachPlaces);
    }

    public Mono<Void> delete(Long packageId) {
        return tourPackageRepository.delete(packageId);
    }

    public Mono<Void> delete(Long packageId, String usuarioEmail, String[] roles) {
        return tourPackageRepository.findById(packageId)
                .flatMap(pkg -> tourPackageRepository.delete(pkg.getId())
                        .then(auditLogRepository.registrar(
                                AuditLog.builder()
                                        .tabla("tour_packages")
                                        .registroId(packageId)
                                        .usuarioEmail(usuarioEmail)
                                        .roles(roles)
                                        .datos(pkg)
                                        .build()
                                )

                        ));
    }

    private Mono<Void> validatePlacesExist(Long[] placeIds) {
        return placeRepository.findByIds(placeIds, DEFAULT_PLACE_LIMIT, DEFAULT_PLACE_OFFSET)
                .collectList()
                .flatMap(found -> found.size() != placeIds.length
                        ? Mono.error(new ConflictException("Uno o más lugares no existen"))
                        : Mono.empty());
    }

    private Mono<TourPackage> resolveFullPackage(TourPackage created) {
        if (created.getId() == null) return Mono.just(created);
        return tourPackageRepository.findById(created.getId())
                .defaultIfEmpty(created);
    }

    private Mono<TourPackage> attachPlaces(TourPackage tourPackage) {
        Long[] placeIds = tourPackage.getPlaceIds();
        if (placeIds == null || placeIds.length == 0) {
            return Mono.just(tourPackage.toBuilder().places(List.of()).build());
        }
        return placeRepository.findByIds(placeIds, DEFAULT_PLACE_LIMIT, DEFAULT_PLACE_OFFSET)
                .collectList()
                .map(places -> tourPackage.toBuilder().places(places).build());
    }

    private static Long[] normalizePlaceIds(Long[] placeIds) {
        if (placeIds == null || placeIds.length == 0) return new Long[0];
        var set = new LinkedHashSet<Long>();
        for (Long id : placeIds) {
            if (id != null && id > 0) set.add(id);
        }
        return set.stream().filter(Objects::nonNull).toArray(Long[]::new);
    }
}