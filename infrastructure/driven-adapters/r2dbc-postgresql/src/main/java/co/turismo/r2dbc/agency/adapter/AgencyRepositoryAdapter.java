package co.turismo.r2dbc.agency.adapter;

import co.turismo.model.agency.Agency;
import co.turismo.model.agency.CreateAgencyRequest;
import co.turismo.model.agency.gateways.AgencyRepository;
import co.turismo.r2dbc.agency.entity.AgencyData;
import co.turismo.r2dbc.agency.repository.AgencyAdapterRepository;
import co.turismo.r2dbc.helper.ReactiveAdapterOperations;
import org.reactivecommons.utils.ObjectMapper;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public class AgencyRepositoryAdapter extends ReactiveAdapterOperations<Agency, AgencyData, Long, AgencyAdapterRepository>
        implements AgencyRepository {

    protected AgencyRepositoryAdapter(AgencyAdapterRepository repository, ObjectMapper mapper) {
        super(repository, mapper, data -> mapper.map(data, Agency.class));
    }

    @Override
    public Mono<Agency> findByUserEmail(String email) {
        return repository.findByUserEmail(email)
                .map(this::toEntity);
    }

    @Override
    public Mono<Agency> create(CreateAgencyRequest request) {
        return repository.insertAgency(
                        request.getName(),
                        request.getDescription(),
                        request.getPhone(),
                        request.getEmail(),
                        request.getWebsite(),
                        request.getLogoUrl()
                )
                .map(this::toEntity);
    }

    @Override
    public Mono<Void> addUserToAgency(Long agencyId, Long userId) {
        return repository.addUserToAgency(agencyId, userId);
    }

    @Override
    public Flux<Agency> findAll() {
        return repository.findAllProjected()
                .map(this::toEntity);
    }
}
