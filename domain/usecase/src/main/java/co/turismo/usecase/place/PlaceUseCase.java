package co.turismo.usecase.place;

import co.turismo.model.place.CreatePlaceRequest;
import co.turismo.model.place.Place;
import co.turismo.model.place.gateways.PlaceRepository;
import co.turismo.model.user.gateways.UserRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class PlaceUseCase {
    private final PlaceRepository placeRepository;
    private final UserRepository userRepository; // <— asegúrate de inyectarlo

    public Mono<Place> createPlaceForOwner(String ownerEmail, String name, String description,
                                           String category, double lat, double lng,
                                           String address, String phone, String website) {
        var req = CreatePlaceRequest.builder()
                .ownerEmail(ownerEmail)
                .name(name).description(description).category(category)
                .lat(lat).lng(lng).address(address).phone(phone).website(website)
                .build();
        return placeRepository.create(req);
    }

    public Flux<Place> findNearby(double lat, double lng, double radiusMeters, int limit) {
        return placeRepository.findNearby(lat, lng, radiusMeters, limit);
    }

    // ←— firma que espera el handler: (adminEmail, placeId, approve)
    public Mono<Place> verifyPlaceByAdmin(String adminEmail, long placeId, boolean approve) {
        return userRepository.findByEmail(adminEmail)
                .switchIfEmpty(Mono.error(new RuntimeException("Admin no encontrado")))
                .flatMap(admin ->
                        placeRepository.verifyPlace(placeId, approve, approve, admin.getId())
                );
    }

    public Mono<Place> setActiveByOwner(String ownerEmail, long placeId, boolean active) {
        // si ya lo migraste a setActiveIfOwner en el gateway, llama a ese:
        return placeRepository.setActiveIfOwner(ownerEmail, placeId, active);
    }

    // (Si añadiste owners y “mis lugares”)
    public Mono<Void> addOwner(String emailToAdd, long placeId) {
        return placeRepository.addOwnerToPlace(emailToAdd, placeId);
    }

    public Mono<Void> removeOwner(String emailToRemove, long placeId) {
        return placeRepository.removeOwnerFromPlace(emailToRemove, placeId);
    }

    public Flux<Place> findMine(String ownerEmail) {
        return placeRepository.findPlacesByOwnerEmail(ownerEmail);
    }
}
