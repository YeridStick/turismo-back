package co.turismo.api.handler;

import co.turismo.api.dto.tourpackage.AdminAgencyTarget;
import co.turismo.api.dto.tourpackage.CreatePackageRequest;
import co.turismo.api.dto.tourpackage.UpdatePackageBody;
import co.turismo.api.dto.response.ApiResponse;
import co.turismo.api.error.RequestValidator;
import co.turismo.api.mapper.TourPackageMapper;
import co.turismo.model.tourpackage.TourPackage;
import co.turismo.usecase.agency.AgencyUseCase;
import co.turismo.usecase.tourpackage.TourPackageUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class TourPackageHandler {

    private static final int MAX_LIMIT = 50;

    private final TourPackageUseCase tourPackageUseCase;
    private final AgencyUseCase agencyUseCase;
    private final RequestValidator requestValidator;

    public Mono<ServerResponse> create(ServerRequest req) {
        return req.principal()
                .cast(Authentication.class)
                .zipWith(req.bodyToMono(CreatePackageRequest.class)
                        .flatMap(requestValidator::validate))
                .flatMap(tuple -> {
                    Authentication auth = tuple.getT1();
                    CreatePackageRequest body = tuple.getT2();
                    var cmd = TourPackageMapper.toCreateCommand(body);

                    if (hasRole(auth, "ADMIN") && body.hasAdminAgencyTarget()) {
                        return resolveAdminTargetAgencyId(body)
                                .flatMap(agencyId -> tourPackageUseCase.createForAgency(cmd, agencyId));
                    }

                    return tourPackageUseCase.create(auth.getName(), cmd);
                })
                .flatMap(pkg -> ServerResponse.status(201)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.created(TourPackageMapper.toResponse(pkg))));
    }

    public Mono<ServerResponse> list(ServerRequest req) {
        int limit  = parseQueryParam(req, "limit",  10);
        int offset = parseQueryParam(req, "offset", 0);

        return tourPackageUseCase.findAll(limit, offset)
                .map(TourPackageMapper::toResponse)
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
                .map(TourPackageMapper::toResponse)
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
                    var cmd = TourPackageMapper.toUpdateCommand(tuple.getT2());

                    if (hasRole(auth, "ADMIN")) {
                        return tourPackageUseCase.update(packageId, cmd);
                    }
                    return verifyOwnership(auth.getName(), packageId)
                            .then(tourPackageUseCase.update(packageId, cmd));
                })
                .flatMap(pkg -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.ok(TourPackageMapper.toResponse(pkg))));
    }

    // ── DELETE ────────────────────────────────────────────────────────────────

    public Mono<ServerResponse> delete(ServerRequest req) {
        long packageId = Long.parseLong(req.pathVariable("id"));

        return req.principal()
                .cast(Authentication.class)
                .flatMap(auth -> {
                    String email = auth.getName();
                    String[] roles = extraerRoles(auth);

                    if (hasRole(auth, "ADMIN")) {
                        return tourPackageUseCase.delete(packageId, email, roles);
                    }
                    return verifyOwnership(email, packageId)
                            .then(tourPackageUseCase.delete(packageId, email, roles));
                })
                .then(ServerResponse.noContent().build());
    }

    // ── PRIVADOS ──────────────────────────────────────────────────────────────

    /** Verifica que el paquete pertenece a la agencia del usuario autenticado. */
    private Mono<Void> verifyOwnership(String userEmail, long packageId) {
        return Mono.zip(
                tourPackageUseCase.findById(packageId, 0, 0),
                agencyUseCase.findByEmail(userEmail)
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

    private String[] extraerRoles(Authentication auth) {
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(r -> r.replace("ROLE_", ""))
                .toArray(String[]::new);
    }

    private static boolean hasRole(Authentication auth, String role) {
        String expected = "ROLE_" + role.toUpperCase();
        return auth.getAuthorities().stream()
                .anyMatch(a -> expected.equalsIgnoreCase(a.getAuthority()));
    }

    private Mono<Long> resolveAdminTargetAgencyId(CreatePackageRequest body) {
        return requestValidator.validate(new AdminAgencyTarget(body.agencyId(), body.agencyEmail()))
                .flatMap(target -> target.agencyId() != null
                        ? Mono.just(target.agencyId())
                        : agencyUseCase.findByEmail(target.agencyEmail()).map(a -> a.getId()));
    }

    private static int parseQueryParam(ServerRequest req, String name, int defaultValue) {
        return req.queryParam(name)
                .map(Integer::parseInt)
                .map(v -> Math.min(MAX_LIMIT, Math.max(0, v)))
                .orElse(defaultValue);
    }

}
