package co.turismo.api.http;

import co.turismo.api.dto.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

public final class HttpResponses {

    private HttpResponses() {}

    public static <T> Mono<ServerResponse> ok(T body) {
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(ApiResponse.ok(body));
    }

    public static <T> Mono<ServerResponse> created(String location, T body) {
        return ServerResponse.created(java.net.URI.create(location))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(ApiResponse.created(body));
    }

    public static Mono<ServerResponse> badRequest(String message) {
        return ServerResponse.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(ApiResponse.error(400, message));
    }

    public static Mono<ServerResponse> conflict(String message) {
        return ServerResponse.status(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(ApiResponse.error(409, message));
    }

    public static Mono<ServerResponse> notFound(String message) {
        return ServerResponse.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(ApiResponse.error(404, message));
    }

    public static Mono<ServerResponse> serverError(String message) {
        return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(ApiResponse.error(500, message));
    }
}
