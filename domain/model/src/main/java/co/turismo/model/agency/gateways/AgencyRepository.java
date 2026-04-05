package co.turismo.model.agency.gateways;

import co.turismo.model.agency.Agency;
import co.turismo.model.agency.CreateAgencyRequest;
import co.turismo.model.agency.UpdateAgencyRequest;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

public interface AgencyRepository {
    Mono<Agency> findByUserEmail(String email);
    Mono<Agency> findById(Long id);
    Mono<Agency> create(CreateAgencyRequest request);
    Mono<Agency> update(Long id, UpdateAgencyRequest request);
    Mono<Void> delete(Long id);
    Mono<Void> addUserToAgency(Long agencyId, Long userId);
    Flux<Agency> findAll();
}
