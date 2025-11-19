package co.turismo.api.handler;

import co.turismo.api.dto.response.ApiResponse;
import co.turismo.model.feedback.Feedback;
import co.turismo.usecase.feedback.FeedbackUseCase;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.*;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class FeedbackHandler {

    private final FeedbackUseCase feedback;

    @Data
    @Schema(name = "CreateFeedbackRequest", description = "Detalle del feedback enviado por un usuario autenticado")
    public static class CreateFeedbackBody {
        @Schema(description = "Tipo de feedback", allowableValues = {"update_info", "suggestion", "complaint", "issue"}, example = "suggestion")
        private String type;
        @Schema(description = "Mensaje descriptivo del feedback", example = "La dirección mostrada ya no es correcta")
        private String message;
        @Schema(description = "Correo para contacto opcional", example = "ana@example.com")
        private String contact_email;
        @Schema(description = "Identificador del dispositivo si no se envía email", example = "android-device-123")
        private String device_id;
    }

    /** POST protegido: crea feedback con email autenticado */
    public Mono<ServerResponse> create(ServerRequest req) {
        Long placeId = Long.valueOf(req.pathVariable("id"));

        return req.principal()
                .cast(Authentication.class)
                .map(Authentication::getName)  // ← email del usuario
                .switchIfEmpty(Mono.error(new IllegalStateException("Usuario no autenticado")))
                .zipWith(req.bodyToMono(CreateFeedbackBody.class)
                        .switchIfEmpty(Mono.error(new IllegalArgumentException("Body requerido"))))
                .flatMap(t -> {
                    String email = t.getT1();
                    CreateFeedbackBody b = t.getT2();
                    return feedback.create(
                            placeId,
                            b.getType(),
                            b.getMessage(),
                            email,                 // ← resolvemos userId por email en el use case
                            b.getDevice_id(),      // (ignorado si hay email)
                            b.getContact_email()
                    );
                })
                .flatMap(f -> ServerResponse.status(201)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(f))
                .onErrorResume(IllegalArgumentException.class,
                        e -> ServerResponse.badRequest()
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(ApiResponse.error(400, e.getMessage())));
    }
}
