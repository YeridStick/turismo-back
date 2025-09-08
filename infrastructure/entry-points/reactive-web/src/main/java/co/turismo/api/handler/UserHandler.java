package co.turismo.api.handler;

import co.turismo.api.http.HttpResponses;
import co.turismo.model.user.UpdateUserProfileRequest;
import co.turismo.model.user.User;
import co.turismo.usecase.user.UserUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
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
                .flatMap(HttpResponses::ok);
    }

    public Mono<ServerResponse> updateMyProfile(ServerRequest req) {
        return req.principal()
                .cast(Authentication.class)
                .map(Authentication::getName)
                .zipWith(req.bodyToMono(UpdateUserProfileRequest.class))
                .flatMap(t -> userUseCase.updateMyProfile(t.getT1(), t.getT2()))
                .flatMap(HttpResponses::ok);
    }
}
