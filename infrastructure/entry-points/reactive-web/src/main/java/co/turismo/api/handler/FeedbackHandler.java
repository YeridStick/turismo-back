package co.turismo.api.handler;

import co.turismo.model.feedback.Feedback;
import co.turismo.usecase.feedback.FeedbackUseCase;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class FeedbackHandler {

    private final FeedbackUseCase feedback;

    @Data
    static class CreateFeedbackBody {
        private String type;           // update_info | suggestion | complaint | issue
        private String message;
        private String contact_email;  // opcional
        private String device_id;      // opcional (no se usará si hay email)
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
                        e -> ServerResponse.badRequest().bodyValue(Map.of("error", e.getMessage())));
    }
}
