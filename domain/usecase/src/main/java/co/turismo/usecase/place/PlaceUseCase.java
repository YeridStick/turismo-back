package co.turismo.usecase.place;

import co.turismo.model.place.Place;
import co.turismo.model.place.gateways.PlaceRepository;
import co.turismo.model.user.gateways.UserRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class PlaceUseCase {
    private final PlaceRepository placeRepository;
    private final UserRepository userRepository;

    public Mono<Place> createPlaceForOwner(String ownerEmail,
                                           String name, String description, String category,
                                           double lat, double lng,
                                           String address, String phone, String website) {

        return userRepository.findByEmail(ownerEmail)
                .switchIfEmpty(Mono.error(new RuntimeException("Owner no encontrado")))
                .flatMap(u -> {
                    Place p = Place.builder()
                            .ownerUserId(u.getId())
                            .name(name)
                            .description(description)
                            .category(category)
                            .address(address)
                            .phone(phone)
                            .website(website)
                            .build();
                    return placeRepository.create(p, lat, lng);
                });
    }

    public Flux<Place> findNearby(double lat, double lng, double radiusMeters, int limit) {
        return placeRepository.findNearby(lat, lng, radiusMeters, limit);
    }

    public Mono<Place> verifyPlaceByAdmin(String adminEmail, long placeId, boolean approve) {
        return userRepository.findByEmail(adminEmail)
                .switchIfEmpty(Mono.error(new RuntimeException("Admin no encontrado")))
                .flatMap(admin -> {
                    boolean verified = approve;
                    boolean active   = approve;
                    return placeRepository.verifyPlace(placeId, verified, active, admin.getId());
                });
    }

    public Mono<Place> setActiveByOwner(String ownerEmail, long placeId, boolean active) {
        return userRepository.findByEmail(ownerEmail)
                .switchIfEmpty(Mono.error(new RuntimeException("Owner no encontrado")))
                .flatMap(owner -> {
                    return placeRepository.setActive(placeId, active);
                });
    }
}
