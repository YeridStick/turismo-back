package co.turismo.model.geocode.gateways;

import co.turismo.model.geocode.GeocodeResult;
import reactor.core.publisher.Mono;

import java.util.List;

public interface GeocodingGateway {
    Mono<List<GeocodeResult>> forward(String address, int limit);
}