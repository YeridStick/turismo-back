package co.turismo.api.handler;

import co.turismo.api.dto.common.SimpleMessageResponse;
import co.turismo.api.dto.response.ApiResponse;
import co.turismo.model.common.AppUrlConfig;
import co.turismo.model.notification.EmailMessage;
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
                .flatMap(body -> {
                    String email = safe(body.email());
                    String subject = safe(body.subject());
                    String message = safe(body.message());
                    boolean html = body.html() != null && body.html();
                    if (email == null || email.isBlank()) {
                        return ServerResponse.badRequest()
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(new SimpleMessageResponse("Email requerido"));
                    }
                    if (subject == null || subject.isBlank()) {
                        return ServerResponse.badRequest()
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(new SimpleMessageResponse("Subject requerido"));
                    }
                    if (message == null || message.isBlank()) {
                        return ServerResponse.badRequest()
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(new SimpleMessageResponse("Mensaje requerido"));
                    }

                    String htmlBody = html ? message : "<pre>" + escapeHtml(message) + "</pre>";
                    return emailGateway.sendEmail(new EmailMessage(email, subject, htmlBody))
                            .then(ServerResponse.ok()
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .bodyValue(ApiResponse.ok(new SimpleMessageResponse("Correo enviado"))));
                })
                .onErrorResume(e -> {
                    String msg = e.getMessage() == null ? "Error enviando correo" : e.getMessage();
                    log.warn("Debug email error: {}", msg);
                    return ServerResponse.badRequest()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(new SimpleMessageResponse(msg));
                });
    }

    public Mono<ServerResponse> sendRecoveryTestEmail(ServerRequest request) {
        return request.bodyToMono(RecoveryEmailRequest.class)
                .flatMap(body -> {
                    String email = safe(body.email());
                    if (email == null || email.isBlank()) {
                        return ServerResponse.badRequest()
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(new SimpleMessageResponse("Email requerido"));
                    }
                    String token = accountRecoveryUseCase.generateRecoveryToken();
                    String link = buildRecoveryLink(token);
                    String html = "<p>Hola,</p><p>Link: <a href=\"" + link + "\">recuperar</a></p>";
                    return emailGateway.sendEmail(new EmailMessage(
                                    email,
                                    "Recupera tu cuenta",
                                    html
                            ))
                            .then(accountRecoveryUseCase.saveRecoveryToken(email, token))
                            .then(ServerResponse.ok()
                                    .header("X-Recovery-Link", link)
                                    .header("X-Recovery-Token", token)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .bodyValue(ApiResponse.ok(new RecoveryEmailResponse(link, token))));
                })
                .onErrorResume(e -> {
                    String msg = e.getMessage() == null ? "Error enviando correo" : e.getMessage();
                    log.warn("Debug recovery email error: {}", msg);
                    return ServerResponse.badRequest()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(new SimpleMessageResponse(msg));
                });
    }

    public Mono<ServerResponse> sendSimpleTestEmail(ServerRequest request) {
        String to = request.queryParam("to").orElse(null);
        if (to == null || to.isBlank()) {
            return ServerResponse.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new SimpleMessageResponse("Falta parámetro 'to'"));
        }

        EmailMessage message = new EmailMessage(
                to,
                "Prueba de Conexión Brevo - Turismo App",
                "<h1>¡Funciona!</h1><p>Este es un correo de prueba enviado desde el backend.</p>"
        );

        return emailGateway.sendEmail(message)
                .then(ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.ok(new SimpleMessageResponse("Correo enviado a " + to))))
                .onErrorResume(e -> {
                    String msg = e.getMessage() == null ? "Error enviando correo" : e.getMessage();
                    log.warn("Debug simple email error: {}", msg);
                    return ServerResponse.badRequest()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(new SimpleMessageResponse(msg));
                });
    }

    private static String safe(String value) {
        return value == null ? null : value.trim();
    }

    private static String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private String buildRecoveryLink(String token) {
        String frontendBase = appUrlConfig.frontendBaseUrl();
        if (frontendBase != null && !frontendBase.isBlank()) {
            String normalized = frontendBase.endsWith("/") ? frontendBase.substring(0, frontendBase.length() - 1) : frontendBase;
            return normalized + "/recover-account?token=" + token;
        }

        String backendBase = appUrlConfig.publicBaseUrl();
        if (backendBase == null || backendBase.isBlank()) {
            return "/recover-account?token=" + token;
        }
        String normalized = backendBase.endsWith("/") ? backendBase.substring(0, backendBase.length() - 1) : backendBase;
        return normalized + "/recover-account?token=" + token;
    }

    public record DebugEmailRequest(
            String email,
            String subject,
            String message,
            Boolean html
    ) {
    }

    public record RecoveryEmailRequest(
            String email
    ) {
    }

    public record RecoveryEmailResponse(
            String link,
            String token
    ) {
    }
}
