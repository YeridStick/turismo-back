package co.turismo.api.handler;

import co.turismo.api.dto.email.DebugEmailRequest;
import co.turismo.api.dto.email.RecoveryEmailRequest;
import co.turismo.api.dto.email.RecoveryEmailResponse;
import co.turismo.api.dto.response.ApiResponse;
import co.turismo.api.mapper.DebugEmailMapper;
import co.turismo.model.common.AppUrlConfig;
import co.turismo.model.notification.gateways.EmailGateway;
import co.turismo.usecase.authenticate.AccountRecoveryUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class DebugEmailHandler {

    private final EmailGateway emailGateway;
    private final AccountRecoveryUseCase accountRecoveryUseCase;
    private final AppUrlConfig appUrlConfig;

    public Mono<ServerResponse> sendTestEmail(ServerRequest request) {
        return request.bodyToMono(DebugEmailRequest.class)
                .flatMap(DebugEmailHandler::validateDebugEmailBody)
                .map(DebugEmailMapper::toDebugEmailMessage)
                .flatMap(emailGateway::sendEmail)
                .then(ok("Correo enviado"))
                .onErrorResume(error -> handleError("Debug email error", error));
    }

    public Mono<ServerResponse> sendRecoveryTestEmail(ServerRequest request) {
        return request.bodyToMono(RecoveryEmailRequest.class)
                .flatMap(DebugEmailHandler::validateRecoveryEmailBody)
                .flatMap(body -> {
                    String email = safe(body.email());
                    String token = accountRecoveryUseCase.generateRecoveryToken();
                    String link = buildRecoveryLink(token);

                    return emailGateway.sendEmail(DebugEmailMapper.toRecoveryEmailMessage(email, link))
                            .then(accountRecoveryUseCase.saveRecoveryToken(email, token))
                            .then(ServerResponse.ok()
                                    .header("X-Recovery-Link", link)
                                    .header("X-Recovery-Token", token)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .bodyValue(ApiResponse.ok(
                                            DebugEmailMapper.toRecoveryEmailResponse(link, token)
                                    )));
                })
                .onErrorResume(error -> handleError("Debug recovery email error", error));
    }

    public Mono<ServerResponse> sendSimpleTestEmail(ServerRequest request) {
        return requiredQueryParam(request, "to", "Falta parámetro 'to'")
                .map(DebugEmailMapper::toSimpleTestEmailMessage)
                .flatMap(emailGateway::sendEmail)
                .then(Mono.defer(() -> {
                    String to = request.queryParam("to")
                            .map(String::trim)
                            .orElse("");

                    return ok("Correo enviado a " + to);
                }))
                .onErrorResume(error -> handleError("Debug simple email error", error));
    }

    private static Mono<DebugEmailRequest> validateDebugEmailBody(DebugEmailRequest body) {
        if (!hasText(body.email())) {
            return Mono.error(new IllegalArgumentException("Email requerido"));
        }

        if (!hasText(body.subject())) {
            return Mono.error(new IllegalArgumentException("Subject requerido"));
        }

        if (!hasText(body.message())) {
            return Mono.error(new IllegalArgumentException("Mensaje requerido"));
        }

        return Mono.just(body);
    }

    private static Mono<RecoveryEmailRequest> validateRecoveryEmailBody(RecoveryEmailRequest body) {
        if (!hasText(body.email())) {
            return Mono.error(new IllegalArgumentException("Email requerido"));
        }

        return Mono.just(body);
    }

    private static Mono<String> requiredQueryParam(ServerRequest request, String param, String message) {
        return Mono.justOrEmpty(request.queryParam(param))
                .map(String::trim)
                .filter(DebugEmailHandler::hasText)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(message)));
    }

    private Mono<ServerResponse> handleError(String logMessage, Throwable error) {
        String message = safeMessage(error, "Error enviando correo");
        log.warn("{}: {}", logMessage, message);

        return ServerResponse.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(DebugEmailMapper.toSimpleMessageResponse(message));
    }

    private static Mono<ServerResponse> ok(String message) {
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(ApiResponse.ok(
                        DebugEmailMapper.toSimpleMessageResponse(message)
                ));
    }

    private String buildRecoveryLink(String token) {
        String frontendBase = appUrlConfig.frontendBaseUrl();

        if (hasText(frontendBase)) {
            return normalizeBaseUrl(frontendBase) + "/recover-account?token=" + token;
        }

        String backendBase = appUrlConfig.publicBaseUrl();

        if (!hasText(backendBase)) {
            return "/recover-account?token=" + token;
        }

        return normalizeBaseUrl(backendBase) + "/recover-account?token=" + token;
    }

    private static String normalizeBaseUrl(String baseUrl) {
        String normalized = baseUrl.trim();

        return normalized.endsWith("/")
                ? normalized.substring(0, normalized.length() - 1)
                : normalized;
    }

    private static String safe(String value) {
        return value == null ? null : value.trim();
    }

    private static String safeMessage(Throwable error, String fallback) {
        String message = error != null ? error.getMessage() : null;
        return hasText(message) ? message : fallback;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}