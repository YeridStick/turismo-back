package co.turismo.api.handler;

import co.turismo.api.dto.auth.*;
import co.turismo.api.dto.response.ApiResponse;
import co.turismo.api.error.RequestValidator;
import co.turismo.api.http.ClientIp;
import co.turismo.api.mapper.AuthenticateMapper;
import co.turismo.model.common.AppUrlConfig;
import co.turismo.model.notification.gateways.EmailGateway;
import co.turismo.usecase.authenticate.AccountRecoveryUseCase;
import co.turismo.usecase.authenticate.AuthenticateUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuthenticateHandler {

    private final AuthenticateUseCase authenticateUseCase;
    private final AccountRecoveryUseCase accountRecoveryUseCase;
    private final AppUrlConfig appUrlConfig;
    private final EmailGateway emailGateway;
    private final RequestValidator requestValidator;

    public Mono<ServerResponse> totpSetup(ServerRequest request) {
        return request.bodyToMono(TotpEmailRequest.class)
                .flatMap(requestValidator::validate)
                .flatMap(req -> authenticateUseCase.setupTotp(normalizeEmail(req.email()), req.password()))
                .map(setup -> AuthenticateMapper.toTotpSetupResponse(
                        setup.secretBase32(),
                        setup.otpAuthUri()
                ))
                .flatMap(AuthenticateHandler::ok);
    }

    public Mono<ServerResponse> totpStatus(ServerRequest request) {
        return requiredEmailQueryParam(request)
                .flatMap(authenticateUseCase::totpStatus)
                .map(AuthenticateMapper::toTotpStatusResponse)
                .flatMap(AuthenticateHandler::ok)
                .onErrorResume(IllegalArgumentException.class, error ->
                        badRequest(error.getMessage())
                );
    }

    public Mono<ServerResponse> totpConfirm(ServerRequest request) {
        return request.bodyToMono(TotpConfirmRequest.class)
                .flatMap(requestValidator::validate)
                .flatMap(req -> authenticateUseCase.confirmTotp(normalizeEmail(req.email()), req.code()))
                .then(ok(AuthenticateMapper.toSimpleMessageResponse("TOTP habilitado")));
    }

    public Mono<ServerResponse> loginTotp(ServerRequest request) {
        return request.bodyToMono(TotpLoginRequest.class)
                .flatMap(requestValidator::validate)
                .flatMap(req -> {
                    String email = normalizeEmail(req.email());
                    String ip = ClientIp.resolve(request.exchange().getRequest());
                    return authenticateUseCase.authenticateTotp(email, req.totpCode(), ip);
                })
                .map(AuthenticateMapper::toJwtTokenResponse)
                .flatMap(AuthenticateHandler::ok);
    }

    public Mono<ServerResponse> loginPassword(ServerRequest request) {
        return request.bodyToMono(PasswordLoginRequest.class)
                .flatMap(requestValidator::validate)
                .flatMap(req -> {
                    String email = normalizeEmail(req.email());
                    String ip = ClientIp.resolve(request.exchange().getRequest());
                    return authenticateUseCase.authenticatePassword(email, req.password(), ip);
                })
                .map(AuthenticateMapper::toJwtTokenResponse)
                .flatMap(AuthenticateHandler::ok)
                .onErrorResume(IllegalArgumentException.class, error ->
                        ServerResponse.badRequest()
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(ApiResponse.error(400, safeMessage(error, "Solicitud inválida")))
                )
                .onErrorResume(RuntimeException.class, error ->
                        ServerResponse.status(HttpStatus.UNAUTHORIZED)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(ApiResponse.error(401, safeMessage(error, "Credenciales inválidas")))
                )
                .onErrorResume(error ->
                        ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(ApiResponse.error(500, safeMessage(error, "Error inesperado")))
                );
    }

    public Mono<ServerResponse> requestEmailVerification(ServerRequest request) {
        return request.bodyToMono(EmailVerificationRequest.class)
                .flatMap(requestValidator::validate)
                .flatMap(req -> accountRecoveryUseCase.requestEmailVerification(normalizeEmail(req.email())))
                .map(AuthenticateMapper::toEmailVerificationResponse)
                .flatMap(AuthenticateHandler::ok);
    }

    public Mono<ServerResponse> verifyEmail(ServerRequest request) {
        String token = request.queryParam("token").orElse("");
        boolean prefersJson = prefersJson(request);

        return accountRecoveryUseCase.verifyEmailToken(token)
                .then(Mono.defer(() -> buildVerificationResponse(
                        token,
                        "ok",
                        "Correo verificado exitosamente",
                        prefersJson,
                        HttpStatus.OK
                )))
                .onErrorResume(error -> buildVerificationResponse(
                        token,
                        "error",
                        safeMessage(error, "Error verificando correo"),
                        prefersJson,
                        HttpStatus.BAD_REQUEST
                ));
    }

    public Mono<ServerResponse> requestRecovery(ServerRequest request) {
        return request.bodyToMono(RecoveryRequest.class)
                .flatMap(requestValidator::validate)
                .flatMap(req -> {
                    String email = normalizeEmail(req.email());
                    String token = accountRecoveryUseCase.generateRecoveryToken();
                    String link = buildRecoveryLink(token);

                    return emailGateway.sendEmail(AuthenticateMapper.toRecoveryEmail(email, link, token))
                            .then(accountRecoveryUseCase.saveRecoveryToken(email, token))
                            .thenReturn(AuthenticateMapper.toRecoveryPayload(link, token));
                })
                .flatMap(payload -> ServerResponse.ok()
                        .header("X-Recovery-Link", payload.get("link"))
                        .header("X-Recovery-Token", payload.get("token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.ok(payload)));
    }

    public Mono<ServerResponse> confirmRecovery(ServerRequest request) {
        return request.bodyToMono(RecoveryConfirmRequest.class)
                .flatMap(requestValidator::validate)
                .flatMap(req -> accountRecoveryUseCase.confirmRecoveryCode(req.token(), req.newPassword()))
                .then(ok(AuthenticateMapper.toSimpleMessageResponse("Contrasena actualizada y TOTP reiniciado")));
    }

    public Mono<ServerResponse> refresh(ServerRequest request) {
        String ip = ClientIp.resolve(request.exchange().getRequest());

        return resolveToken(request)
                .flatMap(token -> authenticateUseCase.refreshSession(token, ip))
                .map(AuthenticateMapper::toJwtTokenResponse)
                .flatMap(AuthenticateHandler::ok)
                .onErrorResume(error -> {
                    String message = safeMessage(error, "No fue posible refrescar el token");
                    log.warn("Refresh token error: {}", message);

                    return ServerResponse.status(HttpStatus.UNAUTHORIZED)
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(AuthenticateMapper.toSimpleMessageResponse(message));
                });
    }

    public Mono<ServerResponse> logout(ServerRequest request) {
        return resolveToken(request)
                .flatMap(authenticateUseCase::logoutSession)
                .then(ok(AuthenticateMapper.toSimpleMessageResponse("Sesion cerrada correctamente")))
                .onErrorResume(IllegalArgumentException.class, error ->
                        badRequest(error.getMessage())
                );
    }

    private Mono<ServerResponse> buildVerificationResponse(
            String token,
            String status,
            String message,
            boolean prefersJson,
            HttpStatus httpStatus
    ) {
        if (prefersJson) {
            Object body = httpStatus.is2xxSuccessful()
                    ? ApiResponse.ok(AuthenticateMapper.toSimpleMessageResponse(message))
                    : AuthenticateMapper.toSimpleMessageResponse(message);

            return ServerResponse.status(httpStatus)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body);
        }

        return redirectToFrontend(token, status, message)
                .switchIfEmpty(ServerResponse.status(httpStatus)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(AuthenticateMapper.toSimpleMessageResponse(message)));
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

    private Mono<ServerResponse> redirectToFrontend(String token, String status, String message) {
        String base = appUrlConfig.frontendBaseUrl();

        if (!hasText(base)) {
            return Mono.empty();
        }

        StringBuilder url = new StringBuilder(normalizeBaseUrl(base))
                .append("/verify-email?status=")
                .append(encode(status));

        if (hasText(token)) {
            url.append("&token=").append(encode(token));
        }

        if (hasText(message)) {
            url.append("&message=").append(encode(message));
        }

        return ServerResponse.temporaryRedirect(URI.create(url.toString())).build();
    }

    private static Mono<String> resolveToken(ServerRequest request) {
        String tokenFromHeader = extractBearer(request.headers().firstHeader(HttpHeaders.AUTHORIZATION));

        return Mono.justOrEmpty(tokenFromHeader)
                .switchIfEmpty(request.bodyToMono(RefreshTokenRequest.class)
                        .flatMap(body -> Mono.justOrEmpty(body.token()))
                        .onErrorResume(error -> Mono.empty()))
                .map(String::trim)
                .filter(token -> !token.isBlank())
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "Token requerido (Authorization Bearer o body)"
                )));
    }

    private static Mono<String> requiredEmailQueryParam(ServerRequest request) {
        return Mono.justOrEmpty(request.queryParam("email"))
                .map(AuthenticateHandler::normalizeEmail)
                .filter(AuthenticateHandler::hasText)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "El parámetro 'email' es requerido"
                )));
    }

    private static Mono<ServerResponse> ok(Object body) {
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(ApiResponse.ok(body));
    }

    private static Mono<ServerResponse> badRequest(String message) {
        return ServerResponse.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(AuthenticateMapper.toSimpleMessageResponse(message));
    }

    private static boolean prefersJson(ServerRequest request) {
        return request.headers()
                .accept()
                .stream()
                .anyMatch(mediaType -> mediaType.isCompatibleWith(MediaType.APPLICATION_JSON));
    }

    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private static String extractBearer(String authorizationHeader) {
        if (!hasText(authorizationHeader)) {
            return null;
        }

        String prefix = "Bearer ";

        return authorizationHeader.regionMatches(true, 0, prefix, 0, prefix.length())
                ? authorizationHeader.substring(prefix.length()).trim()
                : null;
    }

    private static String safeMessage(Throwable error, String fallback) {
        String message = error != null ? error.getMessage() : null;
        return hasText(message) ? message : fallback;
    }

    private static String normalizeBaseUrl(String baseUrl) {
        String normalized = baseUrl.trim();
        return normalized.endsWith("/")
                ? normalized.substring(0, normalized.length() - 1)
                : normalized;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}