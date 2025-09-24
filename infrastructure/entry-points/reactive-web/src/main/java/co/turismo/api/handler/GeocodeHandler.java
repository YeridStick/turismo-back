package co.turismo.api.handler;

import co.turismo.model.geocode.GeocodeResult;
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

    public record Req(String address, Integer limit) {}
    public record Resp<T>(int status, String message, T data) {}

    public Mono<ServerResponse> geocode(ServerRequest req) {

        return req.bodyToMono(Req.class)
                .flatMap(b -> {
                    int limit = b.limit() == null ? 5 : b.limit();
                    return useCase.GeoCodinMany(b.address(), limit);
                })
                .flatMap(data -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(new Resp<>(200, "OK", data)));
    }
}