package co.turismo.model.agency.gateways;

import co.turismo.model.agency.Agency;
import co.turismo.model.agency.CreateAgencyRequest;
import co.turismo.model.agency.UpdateAgencyRequest;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

public interface AgencyRepository {
    /** Retorna la primera agencia vinculada al email (tabla agency_users). */
    Mono<Agency> findByUserEmail(String email);
    /** Retorna TODAS las agencias vinculadas al email (tabla agency_users). */
    Flux<Agency> findAllByUserEmail(String email);
    Mono<Agency> findById(Long id);
    Mono<Agency> create(CreateAgencyRequest request);
    Mono<Agency> update(Long id, UpdateAgencyRequest request);
    Mono<Void> delete(Long id);
    Mono<Void> addUserToAgency(Long agencyId, Long userId);
    Mono<Void> updateAgencyUser(Long agencyId, Long oldUserId, Long newUserId);
    Mono<Void> removeUserFromAgency(Long agencyId, Long userId);
    Flux<Agency> findAll();
}
