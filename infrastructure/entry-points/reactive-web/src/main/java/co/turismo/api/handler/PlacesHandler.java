// infrastructure/entry-points/reactive-web/src/main/java/co/turismo/api/handler/PlacesHandler.java
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

    public Mono<ServerResponse> create(ServerRequest req) {
        return req.principal()
                .cast(Authentication.class)
                .map(Authentication::getName)
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
                            dto.name(),
                            dto.description(),
                            dto.category(),
                            dto.lat(),
                            dto.lng(),
                            dto.address(),
                            dto.phone(),
                            dto.website()
                    );
                })
                .flatMap(place ->
                        ServerResponse.created(req.uri())
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(place)
                );
    }

    public Mono<ServerResponse> findNearby(ServerRequest req) {
        double lat = req.queryParam("lat")
                .map(Double::parseDouble)
                .orElseThrow(() -> new IllegalArgumentException("lat es obligatorio"));
        double lng = req.queryParam("lng")
                .map(Double::parseDouble)
                .orElseThrow(() -> new IllegalArgumentException("lng es obligatorio"));

        double radiusMeters = req.queryParam("radiusMeters")
                .or(() -> req.queryParam("r"))   // alias corto
                .map(Double::parseDouble)
                .orElse(1000.0);

        int limit = req.queryParam("limit")
                .map(Integer::parseInt)
                .orElse(20);

        return placeUseCase.findNearby(lat, lng, radiusMeters, limit)
                .collectList()
                .flatMap(list -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(list));
    }

    // PATCH /api/places/{id}/verify  (ADMIN)
    public Mono<ServerResponse> verify(ServerRequest req) {
        long placeId = Long.parseLong(req.pathVariable("id"));
        return req.principal()
                .cast(Authentication.class)
                .map(Authentication::getName) // adminEmail
                .zipWith(req.bodyToMono(VerifyRequest.class))
                .flatMap(tuple -> placeUseCase.verifyPlaceByAdmin(tuple.getT1(), placeId, tuple.getT2().approve()))
                .flatMap(place -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(place));
    }

    // PATCH /api/places/{id}/active  (OWNER/ADMIN según tu política)
    public Mono<ServerResponse> setActive(ServerRequest req) {
        long placeId = Long.parseLong(req.pathVariable("id"));
        return req.principal()
                .cast(Authentication.class)
                .map(Authentication::getName) // ownerEmail
                .zipWith(req.bodyToMono(ActiveRequest.class))
                .flatMap(tuple -> placeUseCase.setActiveByOwner(tuple.getT1(), placeId, tuple.getT2().active()))
                .flatMap(place -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(place));
    }

    public record VerifyRequest(boolean approve) {}
    public record ActiveRequest(boolean active) {}

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
