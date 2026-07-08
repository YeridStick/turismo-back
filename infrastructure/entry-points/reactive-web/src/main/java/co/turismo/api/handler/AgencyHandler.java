package co.turismo.api.handler;

import co.turismo.api.dto.agency.*;
import co.turismo.api.dto.response.ApiResponse;
import co.turismo.api.dto.visit.TopPlaceDTO;
import co.turismo.api.mapper.AgencyMapper;
import co.turismo.model.agency.Agency;
import co.turismo.model.agency.UpdateAgencyRequest;
import co.turismo.model.place.Place;

import co.turismo.usecase.agency.AgencyUseCase;
import co.turismo.usecase.tourpackage.TourPackageUseCase;
import co.turismo.usecase.user.UserUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class AgencyHandler {

    private static final int MAX_LIMIT = 50;

    private final AgencyUseCase agencyUseCase;
    private final TourPackageUseCase tourPackageUseCase;
    private final UserUseCase userUseCase;

    public Mono<ServerResponse> create(ServerRequest req) {
        return req.principal()
                .cast(Authentication.class)
                .map(Authentication::getName)
                .zipWith(req.bodyToMono(CreateAgencyBody.class))
                .flatMap(tuple -> {
                    String email = tuple.getT1();
                    CreateAgencyBody body = tuple.getT2();
                    return agencyUseCase.create(email, AgencyMapper.toCreateAgencyRequest(body));
                })
                .flatMap(agency -> ServerResponse.status(201)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.created(agency)));
    }

    public Mono<ServerResponse> addUser(ServerRequest req) {
        return req.bodyToMono(AddAgencyUserBody.class)
                .flatMap(body -> agencyUseCase.addUserToMyAgency(body.emailAgencia(), body.email()))
                .flatMap(egency -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.created(egency)));
    }

    public Mono<ServerResponse> list(ServerRequest req) {
        int limit = parseLimit(req.queryParam("limit").orElse(null), 20);
        int offset = parseOffset(req.queryParam("offset").orElse(null), 0);

        return agencyUseCase.findAll(limit, offset)
                .collectList()
                .flatMap(list -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.ok(list)));
    }

    public Mono<ServerResponse> searchByName(ServerRequest req) {
        String query = req.queryParam("q").orElse("");
        int limit = parseLimit(req.queryParam("limit").orElse(null), 20);
        int offset = parseOffset(req.queryParam("offset").orElse(null), 0);

        return agencyUseCase.searchByName(query, limit, offset)
                .collectList()
                .flatMap(list -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.ok(list)));
    }

    public Mono<ServerResponse> findByUser(ServerRequest req) {
        String email = req.queryParam("email").orElse("");
        return agencyUseCase.findByUserEmail(email)
                .collectList()
                .flatMap(agencies -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.ok(agencies)));
    }

    /**
     * Servicio 2: Devuelve TODAS las agencias a las que pertenece el usuario autenticado.
     * Extrae el email del JWT — no requiere ningún rol específico, sólo token válido.
     */
    public Mono<ServerResponse> myAgencies(ServerRequest req) {
        return req.principal()
                .cast(Authentication.class)
                .map(Authentication::getName)
                .flatMapMany(email -> agencyUseCase.findAllByUserEmail(email))
                .collectList()
                .flatMap(list -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.ok(list)));
    }

    /**
     * Servicio 1: Devuelve los paquetes turísticos de una agencia específica (paginado).
     * Es un endpoint público (no requiere token).
     */
    public Mono<ServerResponse> packagesByAgency(ServerRequest req) {
        Long agencyId = Long.parseLong(req.pathVariable("id"));
        int limit = parseLimit(req.queryParam("limit").orElse(null), 20);
        int offset = parseOffset(req.queryParam("offset").orElse(null), 0);

        return tourPackageUseCase.findByAgencyId(agencyId, limit, offset)
                .collectList()
                .flatMap(list -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.ok(list)));
    }

    /**
     * Servicio público: Lista los correos de los usuarios vinculados a una agencia.
     */
    public Mono<ServerResponse> usersByAgency(ServerRequest req) {
        Long agencyId = Long.parseLong(req.pathVariable("id"));
        return userUseCase.findUsersByAgencyId(agencyId)
                .map(user -> new AgencyUserResponse(user.getId(), user.getEmail()))
                .collectList()
                .flatMap(users -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.ok(users)));
    }

    /**
     * Corrige la vinculación de un usuario cambiando su correo.
     */
    public Mono<ServerResponse> updateAgencyUser(ServerRequest req) {
        Long agencyId = Long.parseLong(req.pathVariable("id"));
        Long userId = Long.parseLong(req.pathVariable("userId"));
        return req.principal()
                .cast(Authentication.class)
                .flatMap(auth -> req.bodyToMono(UpdateAgencyUserEmailBody.class)
                        .flatMap(body -> agencyUseCase.updateAgencyUser(auth.getName(), agencyId, userId, body.email())))
                .then(ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.ok("Usuario actualizado en la agencia")));
    }

    /**
     * Elimina la vinculación de un usuario con una agencia.
     */
    public Mono<ServerResponse> removeAgencyUser(ServerRequest req) {
        Long agencyId = Long.parseLong(req.pathVariable("id"));
        Long userId = Long.parseLong(req.pathVariable("userId"));
        return req.principal()
                .cast(Authentication.class)
                .flatMap(auth -> agencyUseCase.removeUserFromAgency(auth.getName(), agencyId, userId))
                .then(ServerResponse.noContent().build());
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
                            .thenMany(agencyUseCase.dashboard(email, from, to, limit))
                            .map(AgencyMapper::toDashboardResponse)
                            .collectList()
                            .flatMap(resp -> ServerResponse.ok()
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .bodyValue(ApiResponse.ok(resp)));
                });
    }

    public Mono<ServerResponse> update(ServerRequest req) {
        Long agencyId = Long.parseLong(req.pathVariable("id"));

        Mono<UpdateAgencyRequest> cmd = req.bodyToMono(UpdateAgencyBody.class)
                .map(AgencyMapper::toUpdateAgencyRequest);

        return req.principal()
                .cast(Authentication.class)
                .zipWith(cmd)
                .flatMap(tuple -> {
                    Authentication auth = tuple.getT1();
                    UpdateAgencyRequest body = tuple.getT2();

                    if (hasRole(auth, "ADMIN")) {
                        return agencyUseCase.update(agencyId, body);
                    }

                    return agencyUseCase.findByUserEmail(auth.getName())
                            .filter(agency -> agency.getId().equals(agencyId))
                            .next()
                            .switchIfEmpty(Mono.error(new ResponseStatusException(
                                    HttpStatus.FORBIDDEN, "No tienes permisos para editar esta agencia")))
                            .flatMap(ignored -> agencyUseCase.update(agencyId, body));
                })
                .flatMap(agency -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.ok(agency)));
    }

    public Mono<ServerResponse> delete(ServerRequest req) {
        Long agencyId = Long.parseLong(req.pathVariable("id"));

        return req.principal()
                .cast(Authentication.class)
                .flatMap(auth -> {
                    String email = auth.getName();
                    String[] roles = extraerRoles(auth);

                    if (hasRole(auth, "ADMIN")) {
                        return agencyUseCase.delete(agencyId, email, roles);
                    }

                    return agencyUseCase.findByUserEmail(email)
                            .filter(agency -> agency.getId().equals(agencyId))
                            .next()
                            .switchIfEmpty(Mono.error(new ResponseStatusException(
                                    HttpStatus.FORBIDDEN, "No tienes permisos para eliminar esta agencia")))
                            .flatMap(ignored -> agencyUseCase.delete(agencyId, email, roles));
                })
                .then(ServerResponse.noContent().build());
    }

    private String[] extraerRoles(Authentication auth) {
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(r -> r.replace("ROLE_", ""))
                .toArray(String[]::new);
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
            return Math.min(MAX_LIMIT, Math.max(1, parsed));
        } catch (Exception e) {
            throw new IllegalArgumentException("limit inválido");
        }
    }

    private static int parseOffset(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (Exception e) {
            throw new IllegalArgumentException("offset inválido");
        }
    }
}
