package co.turismo.api.handler;

import co.turismo.api.dto.auth.*;
import co.turismo.api.dto.common.SimpleMessageResponse;
import co.turismo.api.dto.response.ApiResponse;
import co.turismo.api.http.ClientIp;
import co.turismo.api.util.QrCodeUtil;
import co.turismo.usecase.authenticate.AuthenticateUseCase;
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
public class AuthenticateHandler {

    private final AuthenticateUseCase authenticateUseCase;

    // ---------- SETUP: POST /api/auth/code/setup ----------
    public Mono<ServerResponse> totpSetup(ServerRequest request) {
        return request.bodyToMono(TotpEmailRequest.class)
                .map(req -> normalize(req.email()))
                .flatMap(email ->
                        authenticateUseCase.setupTotp(email)
                                .map(setup -> {
                                    String qrImage = QrCodeUtil.generateQrDataUrl(setup.otpAuthUri());
                                    return new TotpSetupResponse(setup.secretBase32(), setup.otpAuthUri(), qrImage);
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
                                                .bodyValue(new SimpleMessageResponse(msg));
                                    }
                                    return ServerResponse.badRequest()
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .bodyValue(new SimpleMessageResponse(msg));
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
                    .bodyValue(new SimpleMessageResponse("El parámetro 'email' es requerido"));
        }

        return authenticateUseCase.totpStatus(email)
                .flatMap(enabled -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.ok(new TotpStatusResponse(enabled))))
                .onErrorResume(e -> {
                    String msg = e.getMessage() == null ? "Error consultando estado TOTP" : e.getMessage();
                    if (msg.toLowerCase().contains("no encontrado")) {
                        return ServerResponse.status(404)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(new SimpleMessageResponse(msg));
                    }
                    return ServerResponse.badRequest()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(new SimpleMessageResponse(msg));
                });
    }

    // ---------- CONFIRM: POST /api/auth/code/confirm ----------
    public Mono<ServerResponse> totpConfirm(ServerRequest request) {
        return request.bodyToMono(TotpConfirmRequest.class)
                .flatMap(req -> authenticateUseCase.confirmTotp(normalize(req.email()), req.code()))
                .then(ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.ok(new SimpleMessageResponse("TOTP habilitado"))))
                .onErrorResume(e -> {
                    String msg = e.getMessage() == null ? "Error al confirmar TOTP" : e.getMessage();
                    log.error("TOTP confirm error: {}", msg);
                    return ServerResponse.badRequest()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(new SimpleMessageResponse(msg));
                });
    }

    // ---------- LOGIN: POST /api/auth/login-code ----------
    public Mono<ServerResponse> loginTotp(ServerRequest request) {
        return request.bodyToMono(TotpLoginRequest.class)
                .flatMap(req -> {
                    String email = normalize(req.email());
                    String ip = ClientIp.resolve(request.exchange().getRequest());
                    return authenticateUseCase.authenticateTotp(email, req.totpCode(), ip);
                })
                .flatMap(token -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.ok(new JwtTokenResponse(token))))
                .onErrorResume(e -> {
                    String msg = e.getMessage() == null ? "Error en login TOTP" : e.getMessage();
                    log.error("Login TOTP error: {}", msg);
                    return ServerResponse.badRequest()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(new SimpleMessageResponse(msg));
                });
    }

    // ---------- REFRESH: POST /api/auth/refresh ----------
    public Mono<ServerResponse> refresh(ServerRequest request) {
        // 1) Intentar por header Authorization: Bearer <token>
        String bearer = request.headers().firstHeader("Authorization");
        String oldToken = extractBearer(bearer);

        Mono<String> bodyTokenMono = (oldToken != null)
                ? Mono.just(oldToken)
                : request.bodyToMono(RefreshTokenRequest.class).map(RefreshTokenRequest::token);

        String ip = ClientIp.resolve(request.exchange().getRequest());

        return bodyTokenMono
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Token requerido (Authorization Bearer o body)")))
                .flatMap(tok -> authenticateUseCase.refreshSession(tok, ip))
                .flatMap(newToken -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.ok(new JwtTokenResponse(newToken))))
                .onErrorResume(e -> {
                    String msg = e.getMessage() == null ? "No fue posible refrescar el token" : e.getMessage();
                    log.warn("Refresh token error: {}", msg);
                    // Si está fuera de la ventana de gracia / IP no coincide, responde 401
                    return ServerResponse.status(401)
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(new SimpleMessageResponse(msg));
                });
    }

    private static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private static String extractBearer(String authorizationHeader) {
        if (authorizationHeader == null) return null;
        String prefix = "Bearer ";
        return authorizationHeader.startsWith(prefix) ? authorizationHeader.substring(prefix.length()).trim() : null;
    }
}
