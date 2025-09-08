package co.turismo.api.handler;

import co.turismo.api.dto.response.ApiResponse;
import co.turismo.usecase.authenticate.AuthenticateUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AuthenticateHandler {
    private final AuthenticateUseCase authenticateUseCase;

    // POST /api/auth/request-code
    public Mono<ServerResponse> sendVerificationCode(ServerRequest request) {
        return request.bodyToMono(EmailRequest.class)
                .flatMap(body -> authenticateUseCase.sendVerificationCode(body.email()))
                .then(ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.ok(new MessageResponse("Código enviado exitosamente"))));
    }

    // POST /api/auth/verify-code
    public Mono<ServerResponse> authenticate(ServerRequest request) {
        return request.bodyToMono(AuthRequest.class)
                .flatMap(authReq -> {
                    String ip = request.remoteAddress()
                            .map(addr -> addr.getAddress().getHostAddress())
                            .orElse("unknown");
                    return authenticateUseCase.authenticate(authReq.email(), authReq.code(), ip);
                })
                .flatMap(token -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.ok(new TokenResponse(token))));
    }

    // DTOs
    record AuthRequest(String email, String code) {}
    record TokenResponse(String token) {}
    record MessageResponse(String message) {}
    record EmailRequest(String email) {}
}
