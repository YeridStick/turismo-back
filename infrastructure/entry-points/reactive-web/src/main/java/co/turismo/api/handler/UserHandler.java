package co.turismo.api.handler;
 
import co.turismo.api.dto.auth.RegisterUserRequest;
import co.turismo.api.dto.auth.PasswordUpdateRequest;
import co.turismo.api.http.HttpResponses;
import co.turismo.api.error.RequestValidator;
import co.turismo.api.mapper.UserMapper;
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
    private final RequestValidator requestValidator;
 
    public Mono<ServerResponse> createUser(ServerRequest request) {
        return request.bodyToMono(RegisterUserRequest.class)
                .flatMap(requestValidator::validate)
                .map(UserMapper::toRegisterCommand)
                .flatMap(userUseCase::register)
                .flatMap(HttpResponses::ok);
    }
 
    public Mono<ServerResponse> getInfoUser(ServerRequest request) {
        String email = request.queryParam("userEmail").orElse("");
        return userUseCase.getUserInfo(email)
                .flatMap(info -> ServerResponse.ok()
                        .bodyValue(UserMapper.toUserInfoResponse(info)));
    }
 
    public Mono<ServerResponse> updateMyProfile(ServerRequest req) {
        return req.principal()
                .cast(Authentication.class)
                .map(Authentication::getName)
                .zipWith(req.bodyToMono(UpdateUserProfileRequest.class)
                        .flatMap(requestValidator::validate))
                .flatMap(t -> userUseCase.updateMyProfile(t.getT1(), t.getT2()))
                .flatMap(HttpResponses::ok);
    }
 
    public Mono<ServerResponse> setMyPassword(ServerRequest req) {
        return req.principal()
                .cast(Authentication.class)
                .map(Authentication::getName)
                .zipWith(req.bodyToMono(PasswordUpdateRequest.class)
                        .flatMap(requestValidator::validate))
                .flatMap(t -> userUseCase.setPassword(t.getT1(), t.getT2().password()))
                .flatMap(result -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(UserMapper.toPasswordUpdateResponse()));
    }
 
    public Mono<ServerResponse> getAllUsers(ServerRequest req) {
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(userUseCase.getAllUsers(), User.class);
    }
}
