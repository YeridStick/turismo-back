package co.turismo.api.handler;

import co.turismo.api.dto.feedback.CreateFeedbackBody;
import co.turismo.api.dto.response.ApiResponse;
import co.turismo.usecase.feedback.FeedbackUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class FeedbackHandler {

    private final FeedbackUseCase feedback;

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
