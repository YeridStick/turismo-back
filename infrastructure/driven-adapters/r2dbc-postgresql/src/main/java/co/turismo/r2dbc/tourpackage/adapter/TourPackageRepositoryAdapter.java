package co.turismo.r2dbc.tourpackage.adapter;

import co.turismo.model.tourpackage.CreateTourPackageRequest;
import co.turismo.model.tourpackage.TopPackage;
import co.turismo.model.tourpackage.TourPackage;
import co.turismo.model.tourpackage.TourPackageSalesSummary;
import co.turismo.model.tourpackage.gateways.TourPackageRepository;
import co.turismo.r2dbc.helper.ReactiveAdapterOperations;
import co.turismo.r2dbc.tourpackage.dto.TopPackageRowDto;
import co.turismo.r2dbc.tourpackage.dto.TourPackageSalesSummaryRow;
import co.turismo.r2dbc.tourpackage.entity.TourPackageData;
import co.turismo.r2dbc.tourpackage.repository.TourPackageAdapterRepository;
import org.reactivecommons.utils.ObjectMapper;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

@Repository
public class TourPackageRepositoryAdapter
        extends ReactiveAdapterOperations<TourPackage, TourPackageData, Long, TourPackageAdapterRepository>
        implements TourPackageRepository {

    protected TourPackageRepositoryAdapter(TourPackageAdapterRepository repository, ObjectMapper mapper) {
        super(repository, mapper, data -> mapper.map(data, TourPackage.class));
    }

    @Override
    public Mono<TourPackage> create(CreateTourPackageRequest request) {
        return repository.insertPackage(
                request.getAgencyId(),
                request.getTitle(),
                request.getCity(),
                request.getDescription(),
                request.getDays(),
                request.getNights(),
                request.getPeople(),
                request.getRating(),
                request.getReviews(),
                request.getPrice(),
                request.getOriginalPrice(),
                request.getDiscount(),
                request.getTag(),
                request.getIncludes(),
                request.getImage(),
                request.getPlaceIds())
                .map(this::toEntity);
    }

    @Override
    public Flux<TourPackage> findAll(Integer limit, Integer offset) {
        return repository.findAllProjected(limit != null ? limit : 10, offset != null ? offset : 0)
                .map(this::toEntity);
    }

    @Override
    public Mono<TourPackage> findById(Long id) {
        return repository.findByIdProjected(id)
                .map(this::toEntity);
    }

    @Override
    public Flux<TourPackage> findByAgencyId(Long agencyId, Integer limit, Integer offset) {
        return repository.findByAgencyProjected(agencyId,
                        limit != null ? limit : 50,
                        offset != null ? offset : 0)
                .map(this::toEntity);
    }

    @Override
    public Flux<TopPackage> topSoldByAgency(Long agencyId, LocalDate from, LocalDate to, int limit) {
        return repository.topSoldByAgency(agencyId, from, to, limit)
                .map(TourPackageRepositoryAdapter::toTopPackage);
    }

    @Override
    public Mono<TourPackageSalesSummary> salesSummaryByAgency(Long agencyId, LocalDate from, LocalDate to) {
        return repository.salesSummaryByAgency(agencyId, from, to)
                .map(TourPackageRepositoryAdapter::toSalesSummary);
    }

    @Override
    public Mono<TourPackage> update(Long id, co.turismo.model.tourpackage.UpdateTourPackageRequest request) {
        return repository.updatePackage(
                        id,
                        request.getTitle(),
                        request.getCity(),
                        request.getDescription(),
                        request.getDays(),
                        request.getNights(),
                        request.getPeople(),
                        request.getRating(),
                        request.getReviews(),
                        request.getPrice(),
                        request.getOriginalPrice(),
                        request.getDiscount(),
                        request.getTag(),
                        request.getIncludes(),
                        request.getImage()
                )
                .flatMap(updatedPackage -> {
                    if (request.getPlaceIds() != null) {
                        return repository.deletePackagePlaces(id)
                                .then(Flux.fromArray(request.getPlaceIds())
                                        .flatMap(placeId -> repository.addPlaceToPackage(id, placeId))
                                        .then())
                                .thenReturn(updatedPackage);
                    }
                    return Mono.just(updatedPackage);
                })
                .map(this::toEntity);
    }

    @Override
    public Mono<Void> delete(Long id) {
        return repository.deletePackage(id);
    }

    private static TopPackage toTopPackage(TopPackageRowDto row) {
        return TopPackage.builder()
                .packageId(row.getPackageId())
                .title(row.getTitle())
                .sold(row.getSold())
                .revenue(row.getRevenue())
                .build();
    }

    private static TourPackageSalesSummary toSalesSummary(TourPackageSalesSummaryRow row) {
        return TourPackageSalesSummary.builder()
                .totalSold(row.getTotalSold())
                .totalRevenue(row.getTotalRevenue())
                .build();
    }
}
