package co.turismo.api.handler;

import co.turismo.api.dto.response.ApiResponse;
import co.turismo.api.error.RequestValidator;
import co.turismo.model.place.Place;
import co.turismo.model.tourpackage.CreateTourPackageRequest;
import co.turismo.model.tourpackage.TourPackage;
import co.turismo.usecase.agency.AgencyUseCase;
import co.turismo.usecase.tourpackage.TourPackageUseCase;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class TourPackageHandler {

    private final TourPackageUseCase tourPackageUseCase;
    private final AgencyUseCase agencyUseCase;
    private final RequestValidator requestValidator;

    public Mono<ServerResponse> create(ServerRequest req) {
        return req.principal()
                .cast(Authentication.class)
                .map(Authentication::getName)
                .zipWith(req.bodyToMono(CreatePackageRequest.class)
                        .flatMap(requestValidator::validate))
                .flatMap(tuple -> tourPackageUseCase.create(tuple.getT1(), toCreateCommand(tuple.getT2())))
                .flatMap(pkg -> ServerResponse.status(201)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.created(toResponse(pkg))));
    }

    public Mono<ServerResponse> list(ServerRequest req) {
        int limit  = parseQueryParam(req, "limit",  10);
        int offset = parseQueryParam(req, "offset", 0);

        return tourPackageUseCase.findAll(limit, offset)
                .map(this::toResponse)
                .collectList()
                .flatMap(list -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.ok(list)));
    }

    public Mono<ServerResponse> findById(ServerRequest req) {
        long id    = Long.parseLong(req.pathVariable("id"));
        int limit  = parseQueryParam(req, "limit",  10);
        int offset = parseQueryParam(req, "offset", 0);

        return tourPackageUseCase.findById(id, limit, offset)
                .map(this::toResponse)
                .flatMap(pkg -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.ok(pkg)));
    }

    // ── UPDATE ────────────────────────────────────────────────────────────────

    public Mono<ServerResponse> update(ServerRequest req) {
        long packageId = Long.parseLong(req.pathVariable("id"));

        return req.principal()
                .cast(Authentication.class)
                .zipWith(req.bodyToMono(UpdatePackageBody.class)
                        .flatMap(requestValidator::validate))
                .flatMap(tuple -> {
                    Authentication auth = tuple.getT1();
                    var cmd = toUpdateCommand(tuple.getT2());

                    if (hasRole(auth, "ADMIN")) {
                        return tourPackageUseCase.update(packageId, cmd);
                    }
                    return verifyOwnership(auth.getName(), packageId)
                            .then(tourPackageUseCase.update(packageId, cmd));
                })
                .flatMap(pkg -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.ok(toResponse(pkg))));
    }

    // ── DELETE ────────────────────────────────────────────────────────────────

    public Mono<ServerResponse> delete(ServerRequest req) {
        long packageId = Long.parseLong(req.pathVariable("id"));

        return req.principal()
                .cast(Authentication.class)
                .flatMap(auth -> {
                    if (hasRole(auth, "ADMIN")) {
                        return tourPackageUseCase.delete(packageId);
                    }
                    return verifyOwnership(auth.getName(), packageId)
                            .then(tourPackageUseCase.delete(packageId));
                })
                .then(ServerResponse.noContent().build());
    }

    // ── PRIVADOS ──────────────────────────────────────────────────────────────

    /** Verifica que el paquete pertenece a la agencia del usuario autenticado. */
    private Mono<Void> verifyOwnership(String userEmail, long packageId) {
        return Mono.zip(
                tourPackageUseCase.findById(packageId, 0, 0),
                agencyUseCase.findByUserEmail(userEmail)
        ).flatMap(tuple -> {
            TourPackage pkg    = tuple.getT1();
            var         agency = tuple.getT2();
            if (!agency.getId().equals(pkg.getAgencyId())) {
                return Mono.error(new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "No tienes permisos para operar este paquete"));
            }
            return Mono.empty();
        });
    }

    private static CreateTourPackageRequest toCreateCommand(CreatePackageRequest body) {
        return CreateTourPackageRequest.builder()
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
                .placeIds(body.placeIds().toArray(Long[]::new))
                .build();
    }

    private static co.turismo.model.tourpackage.UpdateTourPackageRequest toUpdateCommand(UpdatePackageBody body) {
        return co.turismo.model.tourpackage.UpdateTourPackageRequest.builder()
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
                .includes(body.includes() == null ? null : body.includes().toArray(String[]::new))
                .image(body.image())
                .placeIds(body.placeIds() == null ? null : body.placeIds().toArray(Long[]::new))
                .build();
    }

    private TourPackageResponse toResponse(TourPackage pkg) {
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
                pkg.getIncludes() == null ? List.of() : Arrays.asList(pkg.getIncludes()),
                pkg.getImage(),
                List.of(normalizePlaceIds(pkg.getPlaceIds())),
                pkg.getAgencyId(),
                pkg.getAgencyName(),
                pkg.getPlaces() == null ? List.of() : pkg.getPlaces()
        );
    }

    private static boolean hasRole(Authentication auth, String role) {
        String expected = "ROLE_" + role.toUpperCase();
        return auth.getAuthorities().stream()
                .anyMatch(a -> expected.equalsIgnoreCase(a.getAuthority()));
    }

    private static int parseQueryParam(ServerRequest req, String name, int defaultValue) {
        return req.queryParam(name).map(Integer::parseInt).orElse(defaultValue);
    }

    private static Long[] normalizePlaceIds(Long[] placeIds) {
        if (placeIds == null || placeIds.length == 0) return new Long[0];
        var set = new LinkedHashSet<Long>();
        for (Long id : placeIds) {
            if (id != null && id > 0) set.add(id);
        }
        return set.stream().filter(Objects::nonNull).toArray(Long[]::new);
    }

    // ── DTOs ──────────────────────────────────────────────────────────────────

    @Schema(name = "TourPackageCreateRequest")
    public record CreatePackageRequest(
            @NotBlank(message = "title es obligatorio")
            @Schema(example = "Aventura Completa Huila")
            String title,

            @Schema(example = "Tatacoa / San Agustín")
            String city,

            @NotBlank(message = "description es obligatorio")
            String description,

            @Positive(message = "days debe ser mayor a 0")
            Integer days,

            @PositiveOrZero(message = "nights debe ser mayor o igual a 0")
            Integer nights,

            String people,

            @DecimalMin(value = "0.0", message = "rating debe ser >= 0")
            @DecimalMax(value = "5.0", message = "rating debe ser <= 5")
            Double rating,

            @PositiveOrZero(message = "reviews debe ser >= 0")
            Long reviews,

            @NotNull(message = "price es obligatorio")
            @Positive(message = "price debe ser mayor a 0")
            Long price,

            @Positive(message = "originalPrice debe ser mayor a 0")
            Long originalPrice,

            String discount,
            String tag,
            List<String> includes,
            String image,

            @NotNull(message = "placeIds es obligatorio")
            @Size(min = 1, message = "Debe incluir al menos un lugar")
            List<Long> placeIds
    ) {}

    @Schema(name = "UpdatePackageBody")
    public record UpdatePackageBody(
            @Size(min = 1, message = "title no puede ser vacío si se envía")
            String title,

            String city,

            @Size(min = 1, message = "description no puede ser vacía si se envía")
            String description,

            @Positive(message = "days debe ser mayor a 0")
            Integer days,

            @PositiveOrZero(message = "nights debe ser mayor o igual a 0")
            Integer nights,

            String people,

            @DecimalMin(value = "0.0") @DecimalMax(value = "5.0")
            Double rating,

            @PositiveOrZero Long reviews,

            @Positive(message = "price debe ser mayor a 0")
            Long price,

            @Positive Long originalPrice,

            String discount,
            String tag,
            List<String> includes,
            String image,

            @Size(min = 1, message = "placeIds no puede estar vacío si se envía")
            List<Long> placeIds
    ) {}

    @Schema(name = "TourPackageResponse")
    public record TourPackageResponse(
            String id, String title, String city, String description,
            Integer days, Integer nights, String people,
            Double rating, Long reviews, Long price, Long originalPrice,
            String discount, String tag, List<String> includes, String image,
            List<Long> placeIds, Long agencyId, String agencyName, List<Place> places
    ) {}
}