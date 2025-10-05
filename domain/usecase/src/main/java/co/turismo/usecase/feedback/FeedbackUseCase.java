package co.turismo.usecase.feedback;

import co.turismo.model.feedback.Feedback;
import co.turismo.model.feedback.gateways.FeedbackModelRepository;
import co.turismo.model.userIdentityPort.UserIdentityPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.Set;

@RequiredArgsConstructor
public class FeedbackUseCase {

    private static final Set<String> VALID_TYPES =
            Set.of("update_info", "suggestion", "complaint", "issue");

    private final FeedbackModelRepository feedbackRepository;
    private final UserIdentityPort userIdentityPortGateway;


    public Mono<Feedback> create(
            Long placeId,
            String type,
            String message,
            String email,         // auth obligatorio (tu ruta POST exige autenticación)
            String deviceId,      // ignorado si hay email
            String contactEmail   // opcional
    ) {
        if (type == null || type.isBlank() || !VALID_TYPES.contains(type))
            return Mono.error(new IllegalArgumentException("type inválido. Válidos: " + VALID_TYPES));
        if (message == null || message.isBlank())
            return Mono.error(new IllegalArgumentException("message requerido"));

        final String msg = message.trim();

        return userIdentityPortGateway.getUserIdForEmail(email)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Usuario no encontrado con email: " + email)))
                .flatMap(user -> {
                    var feedback = Feedback.builder()
                            .placeId(placeId)
                            .userId(user.id())     // ← autenticado: seteamos userId
                            .deviceId(null)           // ← ignoramos deviceId si hay email
                            .type(type)
                            .message(msg)
                            .contactEmail(contactEmail)
                            .build();

                    return feedbackRepository.create(feedback);
                });
    }
}
