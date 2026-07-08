package co.turismo.api.handler;

import co.turismo.api.dto.geocode.GeocodeRequest;
import co.turismo.api.dto.response.ApiResponse;
import co.turismo.usecase.geocodeaddress.GeocodeAddressUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class GeocodeHandler {

    private final GeocodeAddressUseCase useCase;

    public Mono<ServerResponse> geocode(ServerRequest req) {

        return req.bodyToMono(GeocodeRequest.class)
                .flatMap(b -> {
                    int limit = b.limit() == null ? 5 : b.limit();
                    int clampedLimit = Math.min(100, Math.max(1, limit));
                    return useCase.GeoCodinMany(b.address(), clampedLimit);
                })
                .flatMap(data -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.ok(data)));
    }
}
