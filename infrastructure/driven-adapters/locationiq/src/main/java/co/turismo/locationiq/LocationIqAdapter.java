package co.turismo.locationiq;

import co.turismo.model.geocode.GeocodeResult;
import co.turismo.model.geocode.gateways.GeocodingGateway;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Repository;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.text.Normalizer;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class LocationIqAdapter implements GeocodingGateway {

    private final WebClient locationIqWebClient;

    @Value("${locationiq.key}")
    private String token;

    private final Cache<String, GeocodeResult> cache =
            Caffeine.newBuilder().maximumSize(10_000).expireAfterWrite(Duration.ofDays(30)).build();

    @Override
    public Mono<List<GeocodeResult>> forward(String rawAddress, int limit) {
        final String q = normalize(rawAddress);
        return locationIqWebClient.get()
                .uri(uri -> uri.path("/search")
                        .queryParam("key", token)
                        .queryParam("format", "json")
                        .queryParam("limit", Math.max(1, Math.min(limit, 10))) // LocationIQ aconseja límites bajos
                        .queryParam("q", q)
                        // Opcional: sesgar a Colombia
                        .queryParam("countrycodes", "co")
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                .map(list -> {
                    if (list == null || list.isEmpty()) return List.of();
                    return list.stream().map(f -> {
                        double lat = Double.parseDouble(String.valueOf(f.get("lat")));
                        double lon = Double.parseDouble(String.valueOf(f.get("lon")));
                        String display = String.valueOf(f.getOrDefault("display_name", q));
                        return new GeocodeResult(lat, lon, "POINT (" + lon + " " + lat + ")", display);
                    }).toList();
                });
    }

    private String normalize(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")      // quita tildes
                .replaceAll("[,\\s]+", " ")    // colapsa comas/espacios
                .trim();
    }
}