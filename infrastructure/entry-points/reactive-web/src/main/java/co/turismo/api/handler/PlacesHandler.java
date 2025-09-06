package co.turismo.api.handler;

import co.turismo.usecase.place.PlaceUseCase;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class PlacesHandler {

    private final PlaceUseCase placeUseCase;

    // POST /api/places
    public Mono<ServerResponse> create(ServerRequest req) {
        return req.principal()
                .cast(Authentication.class)
                .map(Authentication::getName) // ownerEmail autenticado
                .switchIfEmpty(Mono.error(new IllegalStateException("Usuario no autenticado")))
                .zipWith(req.bodyToMono(PlaceCreateRequest.class))
                .flatMap(tuple -> {
                    String ownerEmail = tuple.getT1();
                    PlaceCreateRequest dto = tuple.getT2();

                    if (dto.lat() == null || dto.lng() == null) {
                        return Mono.error(new IllegalArgumentException("lat y lng son obligatorios"));
                    }

                    return placeUseCase.createPlaceForOwner(
                            ownerEmail,
                            dto.name(), dto.description(), dto.category(),
                            dto.lat(), dto.lng(),
                            dto.address(), dto.phone(), dto.website()
                    );
                })
                .flatMap(place -> {
                    var location = req.uriBuilder()
                            .path("/{id}")
                            .build(place.getId());
                    return ServerResponse.created(location)
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(place);
                });
    }

    // GET /api/places/nearby?lat=..&lng=..&radiusMeters=..&limit=..
    public Mono<ServerResponse> findNearby(ServerRequest req) {
        double lat = req.queryParam("lat").map(Double::parseDouble)
                .orElseThrow(() -> new IllegalArgumentException("lat es obligatorio"));
        double lng = req.queryParam("lng").map(Double::parseDouble)
                .orElseThrow(() -> new IllegalArgumentException("lng es obligatorio"));

        double radiusMeters = req.queryParam("radiusMeters")
                .or(() -> req.queryParam("r"))
                .map(Double::parseDouble)
                .orElse(1000.0);

        int limit = req.queryParam("limit").map(Integer::parseInt).orElse(20);

        return placeUseCase.findNearby(lat, lng, radiusMeters, limit)
                .collectList()
                .flatMap(list -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(list));
    }

    // PATCH /admin/places/{id}/verify   (ADMIN)
    public Mono<ServerResponse> verify(ServerRequest req) {
        long placeId = Long.parseLong(req.pathVariable("id"));
        return req.principal()
                .cast(Authentication.class)
                .map(Authentication::getName) // adminEmail (tu use case resuelve el id)
                .zipWith(req.bodyToMono(VerifyRequest.class))
                .flatMap(t -> placeUseCase.verifyPlaceByAdmin(t.getT1(), placeId, t.getT2().approve()))
                .flatMap(place -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(place));
    }

    // PATCH /api/places/{id}/active   (OWNER)
    public Mono<ServerResponse> setActive(ServerRequest req) {
        long placeId = Long.parseLong(req.pathVariable("id"));
        return req.principal()
                .cast(Authentication.class)
                .map(Authentication::getName) // ownerEmail
                .zipWith(req.bodyToMono(ActiveRequest.class))
                .flatMap(t -> placeUseCase.setActiveByOwner(t.getT1(), placeId, t.getT2().active()))
                .flatMap(place -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(place));
    }

    // GET /api/places/mine   (OWNER)
    public Mono<ServerResponse> myPlaces(ServerRequest req) {
        return req.principal()
                .cast(Authentication.class)
                .map(Authentication::getName)
                .flatMapMany(placeUseCase::findMine)
                .collectList()
                .flatMap(list -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(list));
    }

    // POST /api/places/{id}/owners   body: { "email": "..." }  (OWNER/ADMIN según tu política)
    public Mono<ServerResponse> addOwner(ServerRequest req) {
        long placeId = Long.parseLong(req.pathVariable("id"));
        return req.bodyToMono(OwnerEmailRequest.class)
                .flatMap(body -> placeUseCase.addOwner(body.email(), placeId))
                .then(ServerResponse.noContent().build());
    }

    // DELETE /api/places/{id}/owners/{email}  (OWNER/ADMIN)
    public Mono<ServerResponse> removeOwner(ServerRequest req) {
        long placeId = Long.parseLong(req.pathVariable("id"));
        String email = req.pathVariable("email");
        return placeUseCase.removeOwner(email, placeId)
                .then(ServerResponse.noContent().build());
    }

    // DTOs
    public record VerifyRequest(boolean approve) {}
    public record ActiveRequest(boolean active) {}

    public record OwnerEmailRequest(@NotBlank String email) {}

    public record PlaceCreateRequest(
            @NotBlank String name,
            @NotBlank String description,
            @NotBlank String category,
            @NotNull  Double lat,
            @NotNull  Double lng,
            String address,
            String phone,
            String website
    ) {}
}
