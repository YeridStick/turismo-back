package co.turismo.usecase.geocodeaddress;

import co.turismo.model.geocode.GeocodeResult;
import co.turismo.model.geocode.gateways.GeocodingGateway;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.List;

@RequiredArgsConstructor
public class GeocodeAddressUseCase {
    private final GeocodingGateway geocoding;

    public Mono<List<GeocodeResult>> GeoCodinMany(String address, int limit) {
        return geocoding.forward(address, limit);
    }
}