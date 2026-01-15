package co.turismo.model.tourpackage.gateways;

import co.turismo.model.tourpackage.CreateTourPackageRequest;
import co.turismo.model.tourpackage.TopPackage;
import co.turismo.model.tourpackage.TourPackage;
import co.turismo.model.tourpackage.TourPackageSalesSummary;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

public interface TourPackageRepository {
    Mono<TourPackage> create(CreateTourPackageRequest request);
    Flux<TourPackage> findAll();
    Mono<TourPackage> findById(Long id);
    Flux<TourPackage> findByAgencyId(Long agencyId);
    Flux<TopPackage> topSoldByAgency(Long agencyId, LocalDate from, LocalDate to, int limit);
    Mono<TourPackageSalesSummary> salesSummaryByAgency(Long agencyId, LocalDate from, LocalDate to);
}
