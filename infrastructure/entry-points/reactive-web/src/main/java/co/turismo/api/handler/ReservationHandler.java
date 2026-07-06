package co.turismo.api.handler;

import co.turismo.api.dto.response.ApiResponse;
import co.turismo.api.error.RequestValidator;
import co.turismo.model.reservation.ReservationDraft;
import co.turismo.model.reservation.ReservationRequestDetails;
import co.turismo.model.reservation.ReservationStatusChange;
import co.turismo.usecase.reservation.ReservationUseCase;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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
                .map(tuple -> toDetails(tuple.getT1().getName(), tuple.getT2()))
                .flatMap(reservationUseCase::createRequest)
                .map(this::toResponse)
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
                .map(this::toResponse)
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
                .map(this::toResponse)
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
                        toUpdateDetails(tuple.getT1().getName(), tuple.getT2())))
                .map(this::toResponse)
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
                .flatMapMany(auth -> reservationUseCase.findForMyAgency(auth.getName(), status.orElse(null), size, offset))
                .map(this::toResponse)
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
                .map(this::toResponse)
                .collectList()
                .flatMap(list -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.ok(list)));
    }

    public Mono<ServerResponse> agencyReservationById(ServerRequest request) {
        String reservationId = request.pathVariable("reservationId");

        return request.principal()
                .cast(Authentication.class)
                .flatMap(auth -> reservationUseCase.findForMyAgencyById(auth.getName(), reservationId))
                .map(this::toResponse)
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
                .map(this::toResponse)
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
                .map(tuple -> ReservationStatusChange.builder()
                        .reservationId(reservationId)
                        .agencyUserEmail(tuple.getT1().getName())
                        .status(tuple.getT2().status())
                        .notes(tuple.getT2().notes())
                        .build())
                .flatMap(reservationUseCase::updateAgencyStatus)
                .map(this::toResponse)
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
                .map(this::toResponse)
                .flatMap(response -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.ok(response)));
    }

    private ReservationRequestDetails toDetails(String userEmail, CreateReservationBody body) {
        return ReservationRequestDetails.builder()
                .userEmail(userEmail)
                .tourPackageId(body.tourPackageId())
                .startDate(body.startDate())
                .endDate(body.endDate())
                .travelers(body.travelers())
                .customerPhone(body.customerPhone())
                .contactPreference(body.contactPreference())
                .customerMessage(body.message())
                .consentAccepted(body.consentAccepted())
                .consentVersion(body.consentVersion())
                .build();
    }

    private ReservationRequestDetails toUpdateDetails(String userEmail, UpdateReservationBody body) {
        return ReservationRequestDetails.builder()
                .userEmail(userEmail)
                .startDate(body.startDate())
                .endDate(body.endDate())
                .travelers(body.travelers())
                .customerPhone(body.customerPhone())
                .contactPreference(body.contactPreference())
                .customerMessage(body.message())
                .build();
    }

    private ReservationResponse toResponse(ReservationDraft reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getTourPackageId(),
                reservation.getAgencyId(),
                reservation.getPackageTitle(),
                reservation.getTotalAmount(),
                reservation.getCurrency(),
                reservation.getStartDate(),
                reservation.getEndDate(),
                reservation.getTravelers(),
                reservation.getUserEmail(),
                reservation.getCustomerPhone(),
                reservation.getContactPreference(),
                reservation.getCustomerMessage(),
                reservation.getConsentAccepted(),
                reservation.getConsentVersion(),
                reservation.getStatus(),
                reservation.getPaymentProvider(),
                reservation.getPaymentStatus(),
                reservation.getPaymentId(),
                reservation.getPaidAt(),
                reservation.getAgencyNotes(),
                reservation.getContactedAt(),
                reservation.getConfirmedAt(),
                reservation.getCancelledAt(),
                reservation.getCreatedAt()
        );
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

    @JsonIgnoreProperties(ignoreUnknown = false)
    @Schema(name = "CreateReservationRequest")
    public record CreateReservationBody(
            @NotNull(message = "tourPackageId es obligatorio")
            @Schema(example = "6")
            Long tourPackageId,

            @NotNull(message = "startDate es obligatorio")
            @FutureOrPresent(message = "startDate no puede estar en el pasado")
            @Schema(example = "2026-07-20")
            LocalDate startDate,

            @Schema(example = "2026-07-22")
            LocalDate endDate,

            @NotNull(message = "travelers es obligatorio")
            @Min(value = 1, message = "travelers debe ser mayor que cero")
            @Schema(example = "2")
            Integer travelers,

            @Schema(example = "3000000000", description = "Opcional y no verificado. Los canales confiables son EMAIL e IN_APP.")
            String customerPhone,

            @Schema(example = "EMAIL", description = "Canal confiable: EMAIL o IN_APP.")
            String contactPreference,

            @Schema(example = "Deseo confirmar si el paquete incluye transporte")
            String message,

            @NotNull(message = "consentAccepted es obligatorio")
            @AssertTrue(message = "consentAccepted debe ser true")
            @Schema(example = "true")
            Boolean consentAccepted,

            @NotBlank(message = "consentVersion es obligatorio")
            @Schema(example = "2026-07-04")
            String consentVersion
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = false)
    @Schema(name = "UpdateReservationStatusRequest")
    public record UpdateReservationStatusBody(
            @NotBlank(message = "status es obligatorio")
            @Schema(example = "contacted")
            String status,

            @Schema(example = "Se contactó al cliente por el canal autorizado")
            String notes
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = false)
    @Schema(name = "UpdateReservationRequest")
    public record UpdateReservationBody(
            @NotNull(message = "startDate es obligatorio")
            @FutureOrPresent(message = "startDate no puede estar en el pasado")
            @Schema(example = "2026-07-20")
            LocalDate startDate,

            @Schema(example = "2026-07-22")
            LocalDate endDate,

            @NotNull(message = "travelers es obligatorio")
            @Min(value = 1, message = "travelers debe ser mayor que cero")
            @Schema(example = "2")
            Integer travelers,

            @Schema(example = "3000000000")
            String customerPhone,

            @Schema(example = "EMAIL")
            String contactPreference,

            @Schema(example = "Deseo ajustar la fecha")
            String message
    ) {}

    @Schema(name = "DeleteReservationResponse")
    public record DeleteReservationResponse(
            Boolean deleted
    ) {}

    @Schema(name = "ReservationResponse")
    public record ReservationResponse(
            String id,
            Long tourPackageId,
            Long agencyId,
            String packageTitle,
            BigDecimal totalAmount,
            String currency,
            LocalDate startDate,
            LocalDate endDate,
            Integer travelers,
            String customerEmail,
            String customerPhone,
            String contactPreference,
            String message,
            Boolean consentAccepted,
            String consentVersion,
            String status,
            String paymentProvider,
            String paymentStatus,
            String paymentId,
            OffsetDateTime paidAt,
            String agencyNotes,
            OffsetDateTime contactedAt,
            OffsetDateTime confirmedAt,
            OffsetDateTime cancelledAt,
            OffsetDateTime createdAt
    ) {}
}
