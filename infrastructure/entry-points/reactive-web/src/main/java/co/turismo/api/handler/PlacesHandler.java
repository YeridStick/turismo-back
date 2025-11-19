package co.turismo.api.handler;

import co.turismo.api.dto.response.ApiResponse;
import co.turismo.model.place.CreatePlaceRequest;
import co.turismo.model.place.Place;
import co.turismo.usecase.place.PlaceUseCase;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.nio.file.AccessDeniedException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PlacesHandler {

    private final PlaceUseCase placeUseCase;

    public Mono<ServerResponse> create(ServerRequest req) {
        return req.principal()
                .cast(Authentication.class)
                .map(Authentication::getName)               // ← email del owner autenticado
                .switchIfEmpty(Mono.error(new IllegalStateException("Usuario no autenticado")))
                .zipWith(req.bodyToMono(PlaceCreateRequest.class))
                .flatMap(tuple -> {
                    String ownerEmail = tuple.getT1();
                    PlaceCreateRequest dto = tuple.getT2();

                    if (dto.lat() == null || dto.lng() == null)
                        return Mono.error(new IllegalArgumentException("lat y lng son obligatorios"));
                    if (dto.categoryId() == null)
                        return Mono.error(new IllegalArgumentException("categoryId es obligatorio"));

                    var cmd = CreatePlaceRequest.builder()
                            .ownerEmail(ownerEmail)
                            .name(dto.name())
                            .description(dto.description())
                            .categoryId(dto.categoryId())
                            .lat(dto.lat())
                            .lng(dto.lng())
                            .address(dto.address())
                            .phone(dto.phone())
                            .website(dto.website())
                            .imageUrls(dto.imageUrls())
                            .build();

                    return placeUseCase.createPlace(cmd);
                })
                .flatMap(place -> {
                    var location = req.uriBuilder().path("/{id}").build(place.getId());
                    return ServerResponse.created(location)
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(ApiResponse.created(place));
                });
    }

    public Mono<ServerResponse> patch(ServerRequest req) {
        long placeId = Long.parseLong(req.pathVariable("id"));
        return req.bodyToMono(UpdateRequest.class)
                .flatMap(dto -> placeUseCase.patch(placeId, dto.toDomain()))
                .flatMap(place -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.ok(place)));
    }

    public Mono<ServerResponse> findByIdPlace(ServerRequest req) {
        long placeId = Long.parseLong(req.pathVariable("id"));
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(placeUseCase.findByIdPlace(placeId), Place.class);
    }

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
        Long categoryId = req.queryParam("categoryId").map(Long::parseLong).orElse(null);

        return placeUseCase.findNearby(lat, lng, radiusMeters, limit, categoryId)
                .collectList()
                .flatMap(list -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.ok(list)));
    }

    public Mono<ServerResponse> search(ServerRequest req) {
        String q = req.queryParam("q").orElse(null);
        Long categoryId = req.queryParam("categoryId").map(Long::parseLong).orElse(null);

        boolean onlyNearby = req.queryParam("onlyNearby").map(Boolean::parseBoolean).orElse(false);
        Double lat = req.queryParam("lat").map(Double::parseDouble).orElse(null);
        Double lng = req.queryParam("lng").map(Double::parseDouble).orElse(null);
        Double radiusMeters = req.queryParam("radiusMeters")
                .or(() -> req.queryParam("r"))
                .map(Double::parseDouble)
                .orElse(null);

        int page = req.queryParam("page").map(Integer::parseInt).orElse(0);
        int size = req.queryParam("size").map(Integer::parseInt).orElse(20);

        // Validaciones mínimas
        if (page < 0) page = 0;
        if (size <= 0) size = 20;
        if (size > 100) size = 100;

        if (onlyNearby) {
            if (lat == null || lng == null || radiusMeters == null) {
                return Mono.error(new IllegalArgumentException(
                        "Para onlyNearby=true debes enviar lat, lng y radiusMeters"));
            }
        }

        return placeUseCase.search(q, categoryId, onlyNearby, lat, lng, radiusMeters, page, size)
                .collectList()
                .flatMap(list -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.ok(list)));
    }

    public Mono<ServerResponse> findAllPlaces(ServerRequest request) {
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(placeUseCase.findAllPlaces(), Place.class);
    }

    public Mono<ServerResponse> verify(ServerRequest req) {
        long placeId = Long.parseLong(req.pathVariable("id"));
        return req.principal()
                .cast(Authentication.class)
                .map(Authentication::getName)
                .zipWith(req.bodyToMono(VerifyRequest.class))
                .flatMap(t -> placeUseCase.verifyPlaceByAdmin(t.getT1(), placeId, t.getT2().approve()))
                .flatMap(place -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.ok(place)));
    }

    public Mono<ServerResponse> setActive(ServerRequest req) {
        long placeId = Long.parseLong(req.pathVariable("id"));
        return req.principal()
                .cast(Authentication.class)
                .map(Authentication::getName)
                .zipWith(req.bodyToMono(ActiveRequest.class))
                .flatMap(t -> placeUseCase.setActiveByOwner(t.getT1(), placeId, t.getT2().active()))
                .flatMap(place -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.ok(place)));
    }

    public Mono<ServerResponse> myPlaces(ServerRequest req) {
        return req.principal()
                .cast(Authentication.class)
                .map(Authentication::getName)
                .flatMapMany(placeUseCase::findMine)
                .collectList()
                .flatMap(list -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.ok(list)));
    }

    public Mono<ServerResponse> delete(ServerRequest req) {
        long placeId = Long.parseLong(req.pathVariable("id"));

        return req.principal()
                .cast(Authentication.class)
                .map(Authentication::getName)
                .flatMap(email -> placeUseCase.deleteByOwnerOrAdmin(email, placeId))
                .flatMap(deleted ->
                        ServerResponse.ok()
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(ApiResponse.ok(deleted))
                )
                // 403: no es el dueño
                .onErrorResume(AccessDeniedException.class, e ->
                        ServerResponse.status(403)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(ApiResponse.error(403, e.getMessage()))
                )
                // 404: place o usuario no encontrado (según tu mensaje)
                .onErrorResume(e -> {
                    String msg = e.getMessage() != null ? e.getMessage() : "Error";
                    if (msg.toLowerCase().contains("no encontrado")) {
                        return ServerResponse.status(404)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(ApiResponse.error(404, msg));
                    }
                    // Fallback: 400 con mensaje
                    return ServerResponse.status(400)
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(ApiResponse.error(400, msg));
                });
    }

    public Mono<ServerResponse> debug(ServerRequest req) {
        return req.principal()
                .cast(Authentication.class)
                .map(Authentication::getName)
                .flatMap(email -> ServerResponse.ok().bodyValue("Email: " + email));
    }


    @Schema(name = "VerifyPlaceRequest", description = "Aprobación o rechazo desde el panel de administración")
    public record VerifyRequest(
            @Schema(description = "true para aprobar, false para rechazar", example = "true")
            boolean approve
    ) {
    }

    @Schema(name = "SetPlaceActiveRequest", description = "Activa o desactiva un lugar publicado")
    public record ActiveRequest(
            @Schema(description = "true para activar, false para suspender", example = "false")
            boolean active
    ) {
    }

    @Schema(name = "PlaceCreateRequest", description = "Cuerpo necesario para registrar un nuevo lugar turístico")
    public record PlaceCreateRequest(
            @Schema(description = "Nombre comercial del lugar", example = "Café del Parque")
            @NotBlank String name,
            @Schema(description = "Descripción corta que se mostrará en las tarjetas", example = "Espacio cultural con cafés especiales")
            @NotBlank String description,
            @Schema(description = "Identificador de la categoría", example = "12")
            @NotNull Long categoryId,
            @Schema(description = "Latitud decimal", example = "6.25184")
            @NotNull Double lat,
            @Schema(description = "Longitud decimal", example = "-75.56359")
            @NotNull Double lng,
            @Schema(description = "Dirección legible", example = "Cra. 43 #8-31, Medellín")
            String address,
            @Schema(description = "Número de contacto", example = "+57 3011234567")
            String phone,
            @Schema(description = "Sitio web oficial", example = "https://cafedelparque.co")
            String website,
            @Schema(description = "Listado de URLs de imágenes")
            String[] imageUrls
    ) {
    }

    @Schema(name = "PlaceUpdateRequest", description = "Campos opcionales para actualizar parcialmente un lugar")
    public record UpdateRequest(
            @Schema(description = "Nombre comercial del lugar", example = "Café del Parque")
            String name,
            @Schema(description = "Descripción corta que se mostrará en las tarjetas")
            String description,
            @Schema(description = "Identificador de la categoría", example = "12")
            Long categoryId,
            @Schema(description = "Latitud decimal", example = "6.25184")
            Double lat,
            @Schema(description = "Longitud decimal", example = "-75.56359")
            Double lng,
            @Schema(description = "Dirección legible", example = "Cra. 43 #8-31, Medellín")
            String address,
            @Schema(description = "Número de contacto", example = "+57 3011234567")
            String phone,
            @Schema(description = "Sitio web oficial", example = "https://cafedelparque.co")
            String website,
            @Schema(description = "Listado de URLs de imágenes")
            List<String> imageUrls
    ) {
        public co.turismo.model.place.UpdatePlaceRequest toDomain() {
            return co.turismo.model.place.UpdatePlaceRequest.builder()
                    .name(name)
                    .description(description)
                    .categoryId(categoryId)
                    .lat(lat)
                    .lng(lng)
                    .address(address)
                    .phone(phone)
                    .website(website)
                    .imageUrls(imageUrls == null ? null : imageUrls.toArray(String[]::new))
                    .build();
        }
    }
}
