package co.turismo.api.handler;

import co.turismo.api.dto.auth.*;
import co.turismo.api.dto.common.SimpleMessageResponse;
import co.turismo.api.dto.response.ApiResponse;
import co.turismo.api.http.ClientIp;
import co.turismo.api.util.QrCodeUtil;
import co.turismo.model.common.AppUrlConfig;
import co.turismo.model.notification.EmailMessage;
import co.turismo.model.notification.gateways.EmailGateway;
import co.turismo.usecase.authenticate.AccountRecoveryUseCase;
import co.turismo.usecase.authenticate.AuthenticateUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuthenticateHandler {

    private final AuthenticateUseCase authenticateUseCase;
    private final AccountRecoveryUseCase accountRecoveryUseCase;
    private final AppUrlConfig appUrlConfig;
    private final EmailGateway emailGateway;

    // ---------- SETUP: POST /api/auth/code/setup ----------
    public Mono<ServerResponse> totpSetup(ServerRequest request) {
        return request.bodyToMono(TotpEmailRequest.class)
                .flatMap(req -> authenticateUseCase.setupTotp(normalize(req.email()), req.password())
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

    // ---------- LOGIN: POST /api/auth/login-password ----------
    public Mono<ServerResponse> loginPassword(ServerRequest request) {
        return request.bodyToMono(PasswordLoginRequest.class)
                .flatMap(req -> {
                    String email = normalize(req.email());
                    String ip = ClientIp.resolve(request.exchange().getRequest());
                    return authenticateUseCase.authenticatePassword(email, req.password(), ip);
                })
                .flatMap(token -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.ok(new JwtTokenResponse(token))))
                .onErrorResume(e -> {
                    String msg = e.getMessage() == null ? "Error en login" : e.getMessage();
                    log.error("Login password error: {}", msg);
                    return ServerResponse.badRequest()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(new SimpleMessageResponse(msg));
                });
    }

    // ---------- EMAIL VERIFY REQUEST: POST /api/auth/email/request ----------
    public Mono<ServerResponse> requestEmailVerification(ServerRequest request) {
        return request.bodyToMono(EmailVerificationRequest.class)
                .flatMap(req -> accountRecoveryUseCase.requestEmailVerification(normalize(req.email())))
                .flatMap(result -> {
                    String status = result.status().name().toLowerCase();
                    String message = result.status() == AccountRecoveryUseCase.VerificationStatus.ALREADY_VERIFIED
                            ? "Correo ya estaba verificado"
                            : "Correo de verificacion enviado";
                    return ServerResponse.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(ApiResponse.ok(new EmailVerificationResponse(status, message)));
                })
                .onErrorResume(e -> {
                    String msg = e.getMessage() == null ? "Error enviando verificacion" : e.getMessage();
                    return ServerResponse.badRequest()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(new SimpleMessageResponse(msg));
                });
    }

    // ---------- EMAIL VERIFY: GET /api/auth/email/verify?token= ----------
    public Mono<ServerResponse> verifyEmail(ServerRequest request) {
        String token = request.queryParam("token").orElse(null);
        return accountRecoveryUseCase.verifyEmailToken(token)
                .then(redirectToFrontend(token, "ok", null))
                .onErrorResume(e -> {
                    String msg = e.getMessage() == null ? "Error verificando correo" : e.getMessage();
                    return redirectToFrontend(token, "error", msg)
                            .switchIfEmpty(ServerResponse.badRequest()
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .bodyValue(new SimpleMessageResponse(msg)));
                });
    }

    // ---------- RECOVERY: POST /api/auth/recovery/request ----------
    public Mono<ServerResponse> requestRecovery(ServerRequest request) {
        return request.bodyToMono(RecoveryRequest.class)
                .flatMap(req -> {
                    String email = normalize(req.email());
                    String token = accountRecoveryUseCase.generateRecoveryToken();
                    String link = buildRecoveryLink(token);
                    String html = "<p>Hola,</p><p>Link: <a href=\"" + link + "\">recuperar</a></p><p>O usa este c\u00f3digo: <strong>" + token + "</strong></p>";
                    return emailGateway.sendEmail(new EmailMessage(
                                    email,
                                    "Recupera tu cuenta",
                                    html
                            ))
                            .then(accountRecoveryUseCase.saveRecoveryToken(email, token))
                            .thenReturn(Map.of(
                                    "message", "Enlace enviado",
                                    "link", link,
                                    "token", token
                            ));
                })
                .flatMap(payload -> ServerResponse.ok()
                        .header("X-Recovery-Link", payload.get("link").toString())
                        .header("X-Recovery-Token", payload.get("token").toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.ok(payload)))
                .onErrorResume(e -> {
                    String msg = e.getMessage() == null ? "Error en recuperación" : e.getMessage();
                    return ServerResponse.badRequest()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(new SimpleMessageResponse(msg));
                });
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

    // ---------- RECOVERY: POST /api/auth/recovery/confirm ----------
    public Mono<ServerResponse> confirmRecovery(ServerRequest request) {
        return request.bodyToMono(RecoveryConfirmRequest.class)
                .flatMap(req -> accountRecoveryUseCase.confirmRecoveryCode(req.token(), req.newPassword()))
                .then(ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.ok(new SimpleMessageResponse("Contrasena actualizada y TOTP reiniciado"))))
                .onErrorResume(e -> {
                    String msg = e.getMessage() == null ? "Error en recuperación" : e.getMessage();
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

    private Mono<ServerResponse> redirectToFrontend(String token, String status, String message) {
        String base = appUrlConfig.frontendBaseUrl();
        if (base == null || base.isBlank()) {
            return Mono.empty();
        }
        String normalized = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        StringBuilder url = new StringBuilder(normalized)
                .append("/verify-email?status=").append(encode(status));
        if (token != null && !token.isBlank()) {
            url.append("&token=").append(encode(token));
        }
        if (message != null && !message.isBlank()) {
            url.append("&message=").append(encode(message));
        }
        return ServerResponse.temporaryRedirect(java.net.URI.create(url.toString())).build();
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
