package co.turismo.api.http;

import co.turismo.api.dto.response.ApiResponse;
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
}