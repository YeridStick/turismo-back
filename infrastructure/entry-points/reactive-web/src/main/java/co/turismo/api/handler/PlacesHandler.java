package co.turismo.api.handler;

import co.turismo.api.dto.place.SearchRequest;
import co.turismo.api.dto.response.ApiResponse;
import co.turismo.model.place.CreatePlaceRequest;
import co.turismo.model.place.Place;
import co.turismo.model.place.strategy.PlaceSearchCriteria;
import co.turismo.model.place.strategy.PlaceSearchMode;
import co.turismo.usecase.place.PlaceUseCase;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;
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
                            .model3dUrls(dto.model3dUrls())
                            .services(dto.services())
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

    public Mono<ServerResponse> searchFilterPlace(ServerRequest request) {
        PlaceSearchCriteria criteria = mapToCriteria(request);

        return placeUseCase.searchPlace(criteria)
                .collectList()
                .flatMap(list -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.ok(list)));
    }

    public static PlaceSearchCriteria mapToCriteria(ServerRequest request) {
        return PlaceSearchCriteria.builder()
                // Mapeo dinámico: si no existe el parámetro, queda en null (gracias a los Wrappers)
                .mode(request.queryParam("mode").map(PlaceSearchMode::valueOf).orElse(PlaceSearchMode.ALL))
                .q(request.queryParam("q").orElse(null))
                .categoryId(request.queryParam("categoryId").map(Long::valueOf).orElse(null))
                .lat(request.queryParam("lat").map(Double::valueOf).orElse(null))
                .lng(request.queryParam("lng").map(Double::valueOf).orElse(null))
                .radiusMeters(request.queryParam("radius").map(Double::valueOf).orElse(null))
                .onlyNearby(Boolean.parseBoolean(request.queryParam("onlyNearby").orElse("false")))
                .page(Integer.parseInt(request.queryParam("page").orElse("0")))
                .size(Integer.parseInt(request.queryParam("size").orElse("10")))
                .build();
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
        int limit = req.queryParam("limit")
                .map(Integer::parseInt)
                .orElse(10);

        int offset = req.queryParam("offset")
                .map(Integer::parseInt)
                .orElse(0);

        return req.principal()
                .cast(Authentication.class)
                .map(Authentication::getName)
                .flatMapMany(place -> placeUseCase.findMine(place, limit, offset))
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
            String[] imageUrls,
            @Schema(description = "Listado de URLs de modelos 3D")
            String[] model3dUrls,
            @Schema(description = "Listado de servicios (WiFi, Parqueadero, etc.)", example = "[\"WiFi\", \"Piscina\"]")
            String[] services
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
            List<String> imageUrls,
            @Schema(description = "Listado de URLs de modelos 3D")
            List<String> model3dUrls,
            @Schema(description = "Listado de servicios (WiFi, Parqueadero, etc.)")
            List<String> services
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
                    .model3dUrls(model3dUrls == null ? null : model3dUrls.toArray(String[]::new))
                    .services(services == null ? null : services.toArray(String[]::new))
                    .build();
        }
    }
}
