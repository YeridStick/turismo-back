package co.turismo.api.handler;

import co.turismo.api.dto.response.ApiResponse;
import co.turismo.usecase.geocodeaddress.GeocodeAddressUseCase;
import io.swagger.v3.oas.annotations.media.Schema;
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

    @Schema(name = "GeocodeRequest", description = "Dirección que se desea transformar en coordenadas")
    public record Req(
            @Schema(description = "Texto completo de la dirección", example = "Cra. 7 #40-62, Bogotá")
            String address,
            @Schema(description = "Máximo de resultados a retornar (1-100)", example = "5")
            Integer limit
    ) {
    }

    public Mono<ServerResponse> geocode(ServerRequest req) {

        return req.bodyToMono(Req.class)
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
