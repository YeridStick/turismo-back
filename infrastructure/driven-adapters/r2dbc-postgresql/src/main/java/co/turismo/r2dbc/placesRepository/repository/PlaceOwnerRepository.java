package co.turismo.r2dbc.placesRepository.repository;

import co.turismo.r2dbc.placesRepository.entity.PlaceOwnerData;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PlaceOwnerRepository extends R2dbcRepository<PlaceOwnerData, Long> {
    Mono<Void> deleteByPlaceIdAndUserId(Long placeId, Long userId);
    Mono<Boolean> existsByPlaceIdAndUserId(Long placeId, Long userId);
    Flux<PlaceOwnerData> findByUserId(Long userId);
}