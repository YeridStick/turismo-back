package co.turismo.api.handler;

import co.turismo.api.dto.response.ApiResponse;
import co.turismo.api.error.RequestValidator;
import co.turismo.model.reservation.ReservationMessage;
import co.turismo.usecase.reservation.ReservationMessageUseCase;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;

@Component
@RequiredArgsConstructor
public class ReservationMessageHandler {

    private static final int DEFAULT_SIZE = 50;
    private static final int MAX_SIZE = 100;

    private final ReservationMessageUseCase reservationMessageUseCase;
    private final RequestValidator requestValidator;

    public Mono<ServerResponse> customerMessages(ServerRequest request) {
        String reservationId = request.pathVariable("reservationId");
        int size = parseSize(request);
        int offset = parsePage(request) * size;

        return request.principal()
                .cast(Authentication.class)
                .flatMapMany(auth -> reservationMessageUseCase.findForCustomer(auth.getName(), reservationId, size, offset))
                .map(this::toResponse)
                .collectList()
                .flatMap(list -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.ok(list)));
    }

    public Mono<ServerResponse> sendCustomerMessage(ServerRequest request) {
        String reservationId = request.pathVariable("reservationId");

        return request.principal()
                .cast(Authentication.class)
                .zipWith(request.bodyToMono(SendReservationMessageBody.class)
                        .flatMap(requestValidator::validate))
                .flatMap(tuple -> reservationMessageUseCase.sendFromCustomer(
                        tuple.getT1().getName(),
                        reservationId,
                        tuple.getT2().message()))
                .map(this::toResponse)
                .flatMap(response -> ServerResponse.status(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.of(201, "Message sent", response)));
    }

    public Mono<ServerResponse> agencyMessages(ServerRequest request) {
        String reservationId = request.pathVariable("reservationId");
        int size = parseSize(request);
        int offset = parsePage(request) * size;

        return request.principal()
                .cast(Authentication.class)
                .flatMapMany(auth -> reservationMessageUseCase.findForAgency(auth.getName(), reservationId, size, offset))
                .map(this::toResponse)
                .collectList()
                .flatMap(list -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.ok(list)));
    }

    public Mono<ServerResponse> agencyMessagesByAgencyId(ServerRequest request) {
        Long agencyId = parseLong(request.pathVariable("agencyId"), "agencyId inválido");
        String reservationId = request.pathVariable("reservationId");
        int size = parseSize(request);
        int offset = parsePage(request) * size;

        return request.principal()
                .cast(Authentication.class)
                .flatMapMany(auth -> reservationMessageUseCase.findForAgency(
                        auth.getName(),
                        agencyId,
                        hasRole(auth, "ADMIN"),
                        reservationId,
                        size,
                        offset))
                .map(this::toResponse)
                .collectList()
                .flatMap(list -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.ok(list)));
    }

    public Mono<ServerResponse> sendAgencyMessage(ServerRequest request) {
        String reservationId = request.pathVariable("reservationId");

        return request.principal()
                .cast(Authentication.class)
                .zipWith(request.bodyToMono(SendReservationMessageBody.class)
                        .flatMap(requestValidator::validate))
                .flatMap(tuple -> reservationMessageUseCase.sendFromAgency(
                        tuple.getT1().getName(),
                        reservationId,
                        tuple.getT2().message()))
                .map(this::toResponse)
                .flatMap(response -> ServerResponse.status(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.of(201, "Message sent", response)));
    }

    public Mono<ServerResponse> sendAgencyMessageByAgencyId(ServerRequest request) {
        Long agencyId = parseLong(request.pathVariable("agencyId"), "agencyId inválido");
        String reservationId = request.pathVariable("reservationId");

        return request.principal()
                .cast(Authentication.class)
                .zipWith(request.bodyToMono(SendReservationMessageBody.class)
                        .flatMap(requestValidator::validate))
                .flatMap(tuple -> reservationMessageUseCase.sendFromAgency(
                        tuple.getT1().getName(),
                        agencyId,
                        hasRole(tuple.getT1(), "ADMIN"),
                        reservationId,
                        tuple.getT2().message()))
                .map(this::toResponse)
                .flatMap(response -> ServerResponse.status(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.of(201, "Message sent", response)));
    }

    private ReservationMessageResponse toResponse(ReservationMessage message) {
        return new ReservationMessageResponse(
                message.getId(),
                message.getReservationId(),
                message.getSenderEmail(),
                message.getSenderType(),
                message.getBody(),
                message.getCreatedAt()
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
    @Schema(name = "SendReservationMessageRequest")
    public record SendReservationMessageBody(
            @NotBlank(message = "message es obligatorio")
            @Size(max = 2000, message = "message excede la longitud permitida")
            @Schema(example = "Hola, quiero confirmar el proceso de pago.")
            String message
    ) {}

    @Schema(name = "ReservationMessageResponse")
    public record ReservationMessageResponse(
            Long id,
            String reservationId,
            String senderEmail,
            String senderType,
            String message,
            OffsetDateTime createdAt
    ) {}
}
