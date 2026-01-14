package co.turismo.model.tourpackage.gateways;

import co.turismo.model.tourpackage.CreateTourPackageRequest;
import co.turismo.model.tourpackage.TourPackage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TourPackageRepository {
    Mono<TourPackage> create(CreateTourPackageRequest request);
    Flux<TourPackage> findAll();
    Mono<TourPackage> findById(Long id);
}
