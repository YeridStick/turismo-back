package co.turismo.usecase.geocodeaddress;

import co.turismo.model.geocode.GeocodeResult;
import co.turismo.model.geocode.gateways.GeocodingGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeocodeAddressUseCaseTest {

    @Mock
    private GeocodingGateway geocodingGateway;

    private GeocodeAddressUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GeocodeAddressUseCase(geocodingGateway);
    }

    @Test
    void geoCodinManyShouldDelegateToGateway() {
        GeocodeResult result = new GeocodeResult(2.93, -75.28, "POINT(-75.28 2.93)", "Neiva, Huila");
        when(geocodingGateway.forward("Neiva", 5)).thenReturn(Mono.just(List.of(result)));

        StepVerifier.create(useCase.GeoCodinMany("Neiva", 5))
                .assertNext(list -> {
                    org.junit.jupiter.api.Assertions.assertEquals(1, list.size());
                    org.junit.jupiter.api.Assertions.assertEquals(2.93, list.get(0).lat());
                })
                .verifyComplete();

        verify(geocodingGateway).forward("Neiva", 5);
    }
}
