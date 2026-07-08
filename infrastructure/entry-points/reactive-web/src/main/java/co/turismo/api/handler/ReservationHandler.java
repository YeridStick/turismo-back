package co.turismo.api.handler;

import co.turismo.api.dto.reservation.CreateReservationBody;
import co.turismo.api.dto.reservation.DeleteReservationResponse;
import co.turismo.api.dto.reservation.UpdateReservationBody;
import co.turismo.api.dto.reservation.UpdateReservationStatusBody;
import co.turismo.api.dto.response.ApiResponse;
import co.turismo.api.error.RequestValidator;
import co.turismo.api.mapper.ReservationMapper;
import co.turismo.model.reservation.ReservationStatusChange;
import co.turismo.usecase.reservation.ReservationUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ReservationHandler {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    private final ReservationUseCase reservationUseCase;
    private final RequestValidator requestValidator;

    public Mono<ServerResponse> create(ServerRequest request) {
        return request.principal()
                .cast(Authentication.class)
                .zipWith(request.bodyToMono(CreateReservationBody.class)
                        .flatMap(requestValidator::validate))
                .map(tuple -> ReservationMapper.toDetails(tuple.getT1().getName(), tuple.getT2()))
                .flatMap(reservationUseCase::createRequest)
                .map(ReservationMapper::toResponse)
                .flatMap(response -> ServerResponse.status(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.of(201, "Reservation request created", response)));
    }

    public Mono<ServerResponse> myReservations(ServerRequest request) {
        int size = parseSize(request);
        int offset = parsePage(request) * size;

        return request.principal()
                .cast(Authentication.class)
                .flatMapMany(auth -> reservationUseCase.findMine(auth.getName(), size, offset))
                .map(ReservationMapper::toResponse)
                .collectList()
                .flatMap(list -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.ok(list)));
    }

    public Mono<ServerResponse> myReservationById(ServerRequest request) {
        String reservationId = request.pathVariable("reservationId");

        return request.principal()
                .cast(Authentication.class)
                .flatMap(auth -> reservationUseCase.findMineById(auth.getName(), reservationId))
                .map(ReservationMapper::toResponse)
                .flatMap(response -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.ok(response)));
    }

    public Mono<ServerResponse> updateMine(ServerRequest request) {
        String reservationId = request.pathVariable("reservationId");

        return request.principal()
                .cast(Authentication.class)
                .zipWith(request.bodyToMono(UpdateReservationBody.class)
                        .flatMap(requestValidator::validate))
                .flatMap(tuple -> reservationUseCase.updateRequest(
                        tuple.getT1().getName(),
                        reservationId,
                        ReservationMapper.toUpdateDetails(tuple.getT1().getName(), tuple.getT2())))
                .map(ReservationMapper::toResponse)
                .flatMap(response -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.ok(response)));
    }

    public Mono<ServerResponse> deleteMine(ServerRequest request) {
        String reservationId = request.pathVariable("reservationId");

        return request.principal()
                .cast(Authentication.class)
                .flatMap(auth -> reservationUseCase.deleteRequest(auth.getName(), reservationId))
                .flatMap(deleted -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.ok(new DeleteReservationResponse(true))));
    }

    public Mono<ServerResponse> agencyReservations(ServerRequest request) {
        int size = parseSize(request);
        int offset = parsePage(request) * size;
        Optional<String> status = request.queryParam("status")
                .filter(value -> !value.isBlank());

        return request.principal()
                .cast(Authentication.class)
                .flatMapMany(auth -> reservationUseCase.findForMyAgency(
                        auth.getName(),
                        hasRole(auth, "ADMIN"),
                        status.orElse(null),
                        size,
                        offset))
                .map(ReservationMapper::toResponse)
                .collectList()
                .flatMap(list -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.ok(list)));
    }

    public Mono<ServerResponse> agencyReservationsByAgencyId(ServerRequest request) {
        Long agencyId = parseLong(request.pathVariable("agencyId"), "agencyId inválido");
        int size = parseSize(request);
        int offset = parsePage(request) * size;
        Optional<String> status = request.queryParam("status")
                .filter(value -> !value.isBlank());

        return request.principal()
                .cast(Authentication.class)
                .flatMapMany(auth -> reservationUseCase.findForAgency(
                        auth.getName(),
                        agencyId,
                        hasRole(auth, "ADMIN"),
                        status.orElse(null),
                        size,
                        offset))
                .map(ReservationMapper::toResponse)
                .collectList()
                .flatMap(list -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.ok(list)));
    }

    public Mono<ServerResponse> agencyReservationById(ServerRequest request) {
        String reservationId = request.pathVariable("reservationId");

        return request.principal()
                .cast(Authentication.class)
                .flatMap(auth -> reservationUseCase.findForMyAgencyById(
                        auth.getName(),
                        hasRole(auth, "ADMIN"),
                        reservationId))
                .map(ReservationMapper::toResponse)
                .flatMap(response -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.ok(response)));
    }

    public Mono<ServerResponse> agencyReservationByAgencyId(ServerRequest request) {
        Long agencyId = parseLong(request.pathVariable("agencyId"), "agencyId inválido");
        String reservationId = request.pathVariable("reservationId");

        return request.principal()
                .cast(Authentication.class)
                .flatMap(auth -> reservationUseCase.findForAgencyById(
                        auth.getName(),
                        agencyId,
                        hasRole(auth, "ADMIN"),
                        reservationId))
                .map(ReservationMapper::toResponse)
                .flatMap(response -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.ok(response)));
    }

    public Mono<ServerResponse> updateAgencyStatus(ServerRequest request) {
        String reservationId = request.pathVariable("reservationId");

        return request.principal()
                .cast(Authentication.class)
                .zipWith(request.bodyToMono(UpdateReservationStatusBody.class)
                        .flatMap(requestValidator::validate))
                .flatMap(tuple -> reservationUseCase.updateAgencyStatus(
                        ReservationStatusChange.builder()
                                .reservationId(reservationId)
                                .agencyUserEmail(tuple.getT1().getName())
                                .status(tuple.getT2().status())
                                .notes(tuple.getT2().notes())
                                .build(),
                        hasRole(tuple.getT1(), "ADMIN")))
                .map(ReservationMapper::toResponse)
                .flatMap(response -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.ok(response)));
    }

    public Mono<ServerResponse> updateAgencyStatusByAgencyId(ServerRequest request) {
        Long agencyId = parseLong(request.pathVariable("agencyId"), "agencyId inválido");
        String reservationId = request.pathVariable("reservationId");

        return request.principal()
                .cast(Authentication.class)
                .zipWith(request.bodyToMono(UpdateReservationStatusBody.class)
                        .flatMap(requestValidator::validate))
                .flatMap(tuple -> reservationUseCase.updateAgencyStatusForAgency(
                        ReservationStatusChange.builder()
                                .reservationId(reservationId)
                                .agencyUserEmail(tuple.getT1().getName())
                                .status(tuple.getT2().status())
                                .notes(tuple.getT2().notes())
                                .build(),
                        agencyId,
                        hasRole(tuple.getT1(), "ADMIN")))
                .map(ReservationMapper::toResponse)
                .flatMap(response -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.ok(response)));
    }

    private static int parsePage(ServerRequest request) {
        return request.queryParam("page")
                .map(value -> parseInt(value, "page inválido"))
                .map(value -> Math.max(0, value))
                .orElse(0);
    }

    private static int parseSize(ServerRequest request) {
        return request.queryParam("size")
                .map(value -> parseInt(value, "size inválido"))
                .map(value -> Math.min(MAX_SIZE, Math.max(1, value)))
                .orElse(DEFAULT_SIZE);
    }

    private static int parseInt(String value, String message) {
        try {
            return Integer.parseInt(value);
        } catch (Exception error) {
            throw new IllegalArgumentException(message);
        }
    }

    private static Long parseLong(String value, String message) {
        try {
            return Long.parseLong(value);
        } catch (Exception error) {
            throw new IllegalArgumentException(message);
        }
    }

    private static boolean hasRole(Authentication auth, String role) {
        String expected = "ROLE_" + role.toUpperCase();
        return auth.getAuthorities().stream()
                .anyMatch(authority -> expected.equalsIgnoreCase(authority.getAuthority()));
    }

}
