package co.turismo.r2dbc.usersRepository.repository;

import co.turismo.r2dbc.placesRepository.entity.PlaceOwnerData;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface RoleAdapterRepository extends ReactiveCrudRepository<PlaceOwnerData, Long> {
    Mono<Void> deleteByPlaceIdAndUserId(Long placeId, Long userId);
}