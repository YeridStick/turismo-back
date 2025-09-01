package co.turismo.api.handler;

import co.turismo.model.user.User;
import co.turismo.usecase.user.UserUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class UserHandler {
    private final UserUseCase userUseCase;

    public Mono<ServerResponse> createUser(ServerRequest request) {
        return request.bodyToMono(User.class)
                .flatMap(userUseCase::createUser)
                .flatMap(response -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response))
                .onErrorResume(e -> ServerResponse.badRequest().build());
    }
}
