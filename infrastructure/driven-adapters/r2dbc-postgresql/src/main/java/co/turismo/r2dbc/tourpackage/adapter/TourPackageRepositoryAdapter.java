package co.turismo.r2dbc.tourpackage.adapter;

import co.turismo.model.tourpackage.CreateTourPackageRequest;
import co.turismo.model.tourpackage.TourPackage;
import co.turismo.model.tourpackage.gateways.TourPackageRepository;
import co.turismo.r2dbc.helper.ReactiveAdapterOperations;
import co.turismo.r2dbc.tourpackage.entity.TourPackageData;
import co.turismo.r2dbc.tourpackage.repository.TourPackageAdapterRepository;
import org.reactivecommons.utils.ObjectMapper;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public class TourPackageRepositoryAdapter extends ReactiveAdapterOperations<TourPackage, TourPackageData, Long, TourPackageAdapterRepository>
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
                        request.getPlaceIds()
                )
                .map(this::toEntity);
    }

    @Override
    public Flux<TourPackage> findAll() {
        return repository.findAllProjected()
                .map(this::toEntity);
    }

    @Override
    public Mono<TourPackage> findById(Long id) {
        return repository.findByIdProjected(id)
                .map(this::toEntity);
    }
}
