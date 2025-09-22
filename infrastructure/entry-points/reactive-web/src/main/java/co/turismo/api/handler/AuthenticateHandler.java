package co.turismo.api.handler;

import co.turismo.api.dto.response.ApiResponse;
import co.turismo.api.http.ClientIp;
import co.turismo.usecase.authenticate.AuthenticateUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

// Util para generar QR como data:image/png;base64
import co.turismo.api.util.QrCodeUtil;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuthenticateHandler {

    private final AuthenticateUseCase authenticateUseCase;

    // ---------- SETUP: POST /api/auth/code/setup ----------
    public Mono<ServerResponse> totpSetup(ServerRequest request) {
        return request.bodyToMono(EmailReq.class)
                .map(req -> normalize(req.email()))
                .flatMap(email ->
                        authenticateUseCase.setupTotp(email)
                                .map(setup -> {
                                    String qrImage = QrCodeUtil.generateQrDataUrl(setup.otpAuthUri());
                                    return new SetupRes(setup.secretBase32(), setup.otpAuthUri(), qrImage);
                                })
                                .flatMap(res -> ServerResponse.ok()
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .bodyValue(ApiResponse.ok(res))
                                )
                                .onErrorResume(e -> {
                                    String msg = e.getMessage() == null ? "Error en setup" : e.getMessage();
                                    log.warn("TOTP setup error: {}", msg);
                                    if (msg.toLowerCase().contains("ya habilitado")) {
                                        return ServerResponse.status(409)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .bodyValue(new MessageRes(msg));
                                    }
                                    return ServerResponse.badRequest()
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .bodyValue(new MessageRes(msg));
                                })
                );
    }

    // GET /api/auth/code/status?email=
    public Mono<ServerResponse> totpStatus(ServerRequest request) {
        String email = request.queryParam("email")
                .map(e -> e.trim().toLowerCase())
                .orElse(null);

        if (email == null || email.isBlank()) {
            return ServerResponse.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new MessageRes("El parámetro 'email' es requerido"));
        }

        return authenticateUseCase.totpStatus(email)
                .flatMap(enabled -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.ok(new StatusRes(enabled))))
                .onErrorResume(e -> {
                    String msg = e.getMessage() == null ? "Error consultando estado TOTP" : e.getMessage();
                    if (msg.toLowerCase().contains("no encontrado")) {
                        return ServerResponse.status(404)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(new MessageRes(msg));
                    }
                    return ServerResponse.badRequest()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(new MessageRes(msg));
                });
    }

    // ---------- CONFIRM: POST /api/auth/code/confirm ----------
    public Mono<ServerResponse> totpConfirm(ServerRequest request) {
        return request.bodyToMono(ConfirmReq.class)
                .flatMap(req -> authenticateUseCase.confirmTotp(normalize(req.email()), req.code()))
                .then(ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.ok(new MessageRes("TOTP habilitado"))))
                .onErrorResume(e -> {
                    String msg = e.getMessage() == null ? "Error al confirmar TOTP" : e.getMessage();
                    log.error("TOTP confirm error: {}", msg);
                    return ServerResponse.badRequest()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(new MessageRes(msg));
                });
    }

    // ---------- LOGIN: POST /api/auth/login-code ----------
    public Mono<ServerResponse> loginTotp(ServerRequest request) {
        return request.bodyToMono(LoginReq.class)
                .flatMap(req -> {
                    String email = normalize(req.email());
                    // ✅ IP segura (evita NPE detrás de proxy/CDN)
                    String ip = ClientIp.resolve(request.exchange().getRequest());
                    return authenticateUseCase.authenticateTotp(email, req.totpCode(), ip);
                })
                .flatMap(token -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.ok(new TokenRes(token))))
                .onErrorResume(e -> {
                    String msg = e.getMessage() == null ? "Error en login TOTP" : e.getMessage();
                    log.error("Login TOTP error: {}", msg);
                    return ServerResponse.badRequest()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(new MessageRes(msg));
                });
    }

    // ---------- DTOs ----------
    public record EmailReq(String email) {}
    public record ConfirmReq(String email, int code) {}
    public record LoginReq(String email, int totpCode) {}
    public record SetupRes(String secretBase32, String otpAuthUri, String qrImage) {}
    public record TokenRes(String token) {}
    public record MessageRes(String message) {}
    public record StatusRes(boolean enabled) {}

    private static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }
}
