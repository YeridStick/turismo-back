package co.turismo.api.handler;

import co.turismo.api.dto.response.ApiResponse;
import co.turismo.api.dto.visit.TopPlaceDTO;
import co.turismo.model.agency.Agency;
import co.turismo.model.agency.AgencyDashboard;
import co.turismo.model.agency.CreateAgencyRequest;
import co.turismo.model.place.Place;
import co.turismo.model.tourpackage.TourPackage;
import co.turismo.model.tourpackage.TopPackage;
import co.turismo.model.tourpackage.TourPackageSalesSummary;
import co.turismo.usecase.agency.AgencyUseCase;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AgencyHandler {

    private final AgencyUseCase agencyUseCase;

    public Mono<ServerResponse> create(ServerRequest req) {
        return req.principal()
                .cast(Authentication.class)
                .map(Authentication::getName)
                .zipWith(req.bodyToMono(CreateAgencyBody.class))
                .flatMap(tuple -> {
                    String email = tuple.getT1();
                    CreateAgencyBody body = tuple.getT2();
                    var cmd = CreateAgencyRequest.builder()
                            .name(body.name())
                            .description(body.description())
                            .phone(body.phone())
                            .email(body.email())
                            .website(body.website())
                            .logoUrl(body.logoUrl())
                            .build();
                    return agencyUseCase.create(email, cmd);
                })
                .flatMap(agency -> ServerResponse.status(201)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.created(agency)));
    }

    public Mono<ServerResponse> addUser(ServerRequest req) {
        return req.principal()
                .cast(Authentication.class)
                .map(Authentication::getName)
                .zipWith(req.bodyToMono(AddAgencyUserBody.class))
                .flatMap(tuple -> agencyUseCase.addUserToMyAgency(tuple.getT1(), tuple.getT2().email()))
                .flatMap(agency -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.ok(agency)));
    }

    public Mono<ServerResponse> list(ServerRequest req) {
        return agencyUseCase.findAll()
                .collectList()
                .flatMap(list -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.ok(list)));
    }

    public Mono<ServerResponse> findByUser(ServerRequest req) {
        return req.principal()
                .cast(Authentication.class)
                .flatMap(auth -> {
                    String email = req.queryParam("email").orElse(auth.getName());
                    return ensureAllowedEmail(auth, email)
                            .then(agencyUseCase.findByUserEmail(email))
                            .flatMap(agency -> ServerResponse.ok()
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .bodyValue(ApiResponse.ok(agency)));
                });
    }

    public Mono<ServerResponse> dashboard(ServerRequest req) {
        return req.principal()
                .cast(Authentication.class)
                .flatMap(auth -> {
                    String email = req.queryParam("email").orElse(auth.getName());
                    LocalDate to = parseDate(req.queryParam("to").orElse(null), LocalDate.now());
                    LocalDate from = parseDate(req.queryParam("from").orElse(null), to.minusDays(30));
                    int limit = parseLimit(req.queryParam("limit").orElse(null), 10);

                    return ensureAllowedEmail(auth, email)
                            .then(agencyUseCase.dashboard(email, from, to, limit))
                            .map(this::toDashboardResponse)
                            .flatMap(resp -> ServerResponse.ok()
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .bodyValue(ApiResponse.ok(resp)));
                });
    }

    private AgencyDashboardResponse toDashboardResponse(AgencyDashboard dashboard) {
        List<AgencyTourPackageResponse> packages = dashboard.getPackages() == null ? List.of()
                : dashboard.getPackages().stream().map(this::toPackageResponse).toList();
        List<TopPackageResponse> topPackages = dashboard.getTopPackages() == null ? List.of()
                : dashboard.getTopPackages().stream().map(this::toTopPackageResponse).toList();
        List<TopPlaceDTO> topPlaces = dashboard.getTopPlaces() == null ? List.of()
                : dashboard.getTopPlaces().stream()
                .map(p -> new TopPlaceDTO(p.getPlaceId(), p.getName(), p.getVisits()))
                .toList();

        return new AgencyDashboardResponse(
                dashboard.getAgency(),
                packages,
                topPackages,
                topPlaces,
                toSalesSummaryResponse(dashboard.getSalesSummary())
        );
    }

    private AgencyTourPackageResponse toPackageResponse(TourPackage pkg) {
        List<String> includes = pkg.getIncludes() == null ? List.of() : Arrays.asList(pkg.getIncludes());
        List<Long> placeIds = pkg.getPlaceIds() == null ? List.of() : Arrays.asList(pkg.getPlaceIds());
        List<Place> places = pkg.getPlaces() == null ? List.of() : pkg.getPlaces();

        return new AgencyTourPackageResponse(
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

    private TopPackageResponse toTopPackageResponse(TopPackage pkg) {
        return new TopPackageResponse(
                pkg.getPackageId(),
                pkg.getTitle(),
                pkg.getSold(),
                pkg.getRevenue()
        );
    }

    private SalesSummaryResponse toSalesSummaryResponse(TourPackageSalesSummary summary) {
        if (summary == null) {
            return new SalesSummaryResponse(0L, 0L);
        }
        return new SalesSummaryResponse(summary.getTotalSold(), summary.getTotalRevenue());
    }

    private static Mono<Void> ensureAllowedEmail(Authentication auth, String email) {
        if (hasRole(auth, "ADMIN")) {
            return Mono.empty();
        }
        if (email == null || !email.equalsIgnoreCase(auth.getName())) {
            return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden"));
        }
        return Mono.empty();
    }

    private static boolean hasRole(Authentication auth, String role) {
        String expected = "ROLE_" + role.toUpperCase();
        return auth.getAuthorities().stream()
                .anyMatch(a -> expected.equalsIgnoreCase(a.getAuthority()));
    }

    private static LocalDate parseDate(String value, LocalDate fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return LocalDate.parse(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Formato de fecha inválido (yyyy-MM-dd)");
        }
    }

    private static int parseLimit(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : fallback;
        } catch (Exception e) {
            throw new IllegalArgumentException("limit inválido");
        }
    }

    @Schema(name = "CreateAgencyRequest", description = "Cuerpo para crear una agencia")
    public record CreateAgencyBody(
            @Schema(description = "Nombre de la agencia", example = "Turismo Huila")
            @NotBlank String name,
            @Schema(description = "Descripción corta")
            String description,
            @Schema(description = "Teléfono de contacto")
            String phone,
            @Schema(description = "Email de la agencia")
            String email,
            @Schema(description = "Sitio web")
            String website,
            @Schema(description = "Logo URL")
            String logoUrl
    ) {
    }

    @Schema(name = "AddAgencyUserRequest", description = "Asociar usuario a la agencia del solicitante")
    public record AddAgencyUserBody(
            @Schema(description = "Email del usuario a asociar", example = "nuevo@correo.com")
            @NotBlank String email
    ) {
    }

    @Schema(name = "AgencyDashboard", description = "Resumen de métricas de la agencia")
    public record AgencyDashboardResponse(
            @Schema(description = "Información de la agencia")
            Agency agency,
            @Schema(description = "Paquetes de la agencia")
            List<AgencyTourPackageResponse> packages,
            @Schema(description = "Paquetes más vendidos en el rango")
            List<TopPackageResponse> topPackages,
            @Schema(description = "Lugares más visitados en el rango")
            List<TopPlaceDTO> topPlaces,
            @Schema(description = "Resumen de ventas en el rango")
            SalesSummaryResponse salesSummary
    ) {
    }

    @Schema(name = "TopPackage", description = "Paquete con mayor número de ventas")
    public record TopPackageResponse(
            @Schema(description = "ID del paquete", example = "101")
            Long packageId,
            @Schema(description = "Título del paquete")
            String title,
            @Schema(description = "Cantidad vendida", example = "24")
            Integer sold,
            @Schema(description = "Ingresos acumulados", example = "12000000")
            Long revenue
    ) {
    }

    @Schema(name = "TourPackageSalesSummary", description = "Totales de ventas")
    public record SalesSummaryResponse(
            @Schema(description = "Total de personas vendidas", example = "120")
            Long totalSold,
            @Schema(description = "Ingresos acumulados", example = "48000000")
            Long totalRevenue
    ) {
    }

    @Schema(name = "AgencyTourPackageResponse", description = "Paquete asociado a la agencia")
    public record AgencyTourPackageResponse(
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
