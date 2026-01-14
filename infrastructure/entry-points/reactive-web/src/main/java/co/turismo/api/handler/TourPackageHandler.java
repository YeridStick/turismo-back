package co.turismo.api.handler;

import co.turismo.api.dto.response.ApiResponse;
import co.turismo.model.place.Place;
import co.turismo.model.tourpackage.CreateTourPackageRequest;
import co.turismo.model.tourpackage.TourPackage;
import co.turismo.usecase.tourpackage.TourPackageUseCase;
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

import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
public class TourPackageHandler {

    private final TourPackageUseCase tourPackageUseCase;

    public Mono<ServerResponse> create(ServerRequest req) {
        return req.principal()
                .cast(Authentication.class)
                .map(Authentication::getName)
                .zipWith(req.bodyToMono(CreatePackageRequest.class))
                .flatMap(tuple -> {
                    String email = tuple.getT1();
                    CreatePackageRequest body = tuple.getT2();

                    var cmd = CreateTourPackageRequest.builder()
                            .title(body.title())
                            .city(body.city())
                            .description(body.description())
                            .days(body.days())
                            .nights(body.nights())
                            .people(body.people())
                            .rating(body.rating())
                            .reviews(body.reviews())
                            .price(body.price())
                            .originalPrice(body.originalPrice())
                            .discount(body.discount())
                            .tag(body.tag())
                            .includes(body.includes() == null ? new String[0] : body.includes().toArray(String[]::new))
                            .image(body.image())
                            .placeIds(body.placeIds() == null ? new Long[0] : body.placeIds().toArray(Long[]::new))
                            .build();

                    return tourPackageUseCase.create(email, cmd);
                })
                .flatMap(pkg -> ServerResponse.status(201)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.created(toResponse(pkg))));
    }

    public Mono<ServerResponse> list(ServerRequest req) {
        return tourPackageUseCase.findAll()
                .map(this::toResponse)
                .collectList()
                .flatMap(list -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.ok(list)));
    }

    public Mono<ServerResponse> findById(ServerRequest req) {
        long id = Long.parseLong(req.pathVariable("id"));
        return tourPackageUseCase.findById(id)
                .map(this::toResponse)
                .flatMap(pkg -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.ok(pkg)));
    }

    private TourPackageResponse toResponse(TourPackage pkg) {
        List<String> includes = pkg.getIncludes() == null ? List.of() : Arrays.asList(pkg.getIncludes());
        List<Long> placeIds = pkg.getPlaceIds() == null ? List.of() : Arrays.asList(pkg.getPlaceIds());
        List<Place> places = pkg.getPlaces() == null ? List.of() : pkg.getPlaces();

        return new TourPackageResponse(
                pkg.getId() == null ? null : String.valueOf(pkg.getId()),
                pkg.getTitle(),
                pkg.getCity(),
                pkg.getDescription(),
                pkg.getDays(),
                pkg.getNights(),
                pkg.getPeople(),
                pkg.getRating(),
                pkg.getReviews(),
                pkg.getPrice(),
                pkg.getOriginalPrice(),
                pkg.getDiscount(),
                pkg.getTag(),
                includes,
                pkg.getImage(),
                placeIds,
                pkg.getAgencyId(),
                pkg.getAgencyName(),
                places
        );
    }

    @Schema(name = "TourPackageCreateRequest", description = "Cuerpo para crear un paquete turístico")
    public record CreatePackageRequest(
            @Schema(description = "Título visible del paquete", example = "Aventura Completa Huila")
            @NotBlank String title,
            @Schema(description = "Ciudad o zona principal", example = "Tatacoa / San Agustín")
            String city,
            @Schema(description = "Descripción corta para el carrusel")
            @NotBlank String description,
            @Schema(description = "Número de días", example = "5")
            Integer days,
            @Schema(description = "Número de noches", example = "4")
            Integer nights,
            @Schema(description = "Texto libre sobre capacidad", example = "Hasta 8 personas")
            String people,
            @Schema(description = "Rating promedio", example = "4.9")
            Double rating,
            @Schema(description = "Cantidad de reseñas", example = "342")
            Long reviews,
            @Schema(description = "Precio actual", example = "1890000")
            @NotNull Long price,
            @Schema(description = "Precio original antes de descuento", example = "2500000")
            Long originalPrice,
            @Schema(description = "Texto de descuento", example = "-24%")
            String discount,
            @Schema(description = "Etiqueta del paquete", example = "Destacado")
            String tag,
            @Schema(description = "Incluye (lista de strings)")
            List<String> includes,
            @Schema(description = "URL de imagen principal")
            String image,
            @Schema(description = "IDs de lugares asociados", example = "[101, 102]")
            @NotNull List<Long> placeIds
    ) {
    }

    @Schema(name = "TourPackageResponse", description = "Respuesta pública de un paquete turístico")
    public record TourPackageResponse(
            @Schema(description = "Identificador del paquete", example = "1")
            String id,
            @Schema(description = "Título visible del paquete")
            String title,
            @Schema(description = "Ciudad o zona principal")
            String city,
            @Schema(description = "Descripción corta del paquete")
            String description,
            @Schema(description = "Número de días")
            Integer days,
            @Schema(description = "Número de noches")
            Integer nights,
            @Schema(description = "Texto de capacidad")
            String people,
            @Schema(description = "Rating promedio")
            Double rating,
            @Schema(description = "Cantidad de reseñas")
            Long reviews,
            @Schema(description = "Precio actual")
            Long price,
            @Schema(description = "Precio original antes de descuento")
            Long originalPrice,
            @Schema(description = "Texto de descuento")
            String discount,
            @Schema(description = "Etiqueta del paquete")
            String tag,
            @Schema(description = "Incluye (lista de strings)")
            List<String> includes,
            @Schema(description = "URL de imagen principal")
            String image,
            @Schema(description = "IDs de lugares asociados")
            List<Long> placeIds,
            @Schema(description = "ID de la agencia responsable")
            Long agencyId,
            @Schema(description = "Nombre de la agencia responsable")
            String agencyName,
            @Schema(description = "Listado de lugares asociados")
            List<Place> places
    ) {
    }
}
