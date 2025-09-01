package co.turismo.api.handler;

import co.turismo.api.Handler;
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

    public Mono<ServerResponse> sendVerificationCode(ServerRequest request) {
        return request.bodyToMono(AuthenticateHandler.EmailRequest.class)
                .flatMap(emailRequest -> authenticateUseCase.sendVerificationCode(emailRequest.getEmail()))
                .flatMap(code -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(new AuthenticateHandler.MessageResponse("Código enviado exitosamente")))
                .onErrorResume(e -> {
                    return ServerResponse.badRequest()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(new AuthenticateHandler.MessageResponse(e.getMessage()));
                });
    }

    public Mono<ServerResponse> authenticate(ServerRequest request) {
        return request.bodyToMono(AuthenticateHandler.AuthRequest.class)
                .flatMap(authReq -> {
                    String ip = request.remoteAddress()
                            .map(addr -> addr.getAddress().getHostAddress())
                            .orElse("unknown");
                    return authenticateUseCase.authenticate(authReq.email(), authReq.code(), ip);
                })
                .flatMap(token -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(new AuthenticateHandler.TokenResponse(token)))
                .onErrorResume(e -> {
                    return ServerResponse.badRequest()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(new AuthenticateHandler.MessageResponse(e.getMessage()));
                });
    }


    record AuthRequest(String email, String code) {}
    record TokenResponse(String token) {}
    record MessageResponse(String message) {}
    record EmailRequest(String email) {
        public String getEmail() {
            return email;
        }
    }
}
