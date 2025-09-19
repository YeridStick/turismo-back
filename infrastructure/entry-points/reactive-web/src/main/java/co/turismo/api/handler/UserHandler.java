package co.turismo.api.handler;

import co.turismo.api.http.HttpResponses;
import co.turismo.model.place.Place;
import co.turismo.model.user.UpdateUserProfileRequest;
import co.turismo.model.user.User;
import co.turismo.usecase.user.UserUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
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

    public Mono<ServerResponse> getInfoUser(ServerRequest request) {
        String email = request.queryParam("userEmail").orElse("");
        return userUseCase.getUserByEmail(email)
                .flatMap(user -> ServerResponse.ok().bodyValue(user));
    }

    public Mono<ServerResponse> updateMyProfile(ServerRequest req) {
        return req.principal()
                .cast(Authentication.class)
                .map(Authentication::getName)
                .zipWith(req.bodyToMono(UpdateUserProfileRequest.class))
                .flatMap(t -> userUseCase.updateMyProfile(t.getT1(), t.getT2()))
                .flatMap(HttpResponses::ok);
    }

    public Mono<ServerResponse> getAllUsers(ServerRequest req) {
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(userUseCase.getAllUsers(), User.class);
    }
}
