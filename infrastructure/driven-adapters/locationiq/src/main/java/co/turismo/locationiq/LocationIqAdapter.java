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
            Caffeine.newBuilder().maximumSize(1_000).expireAfterWrite(Duration.ofHours(6)).build();

    @Override
    public Mono<List<GeocodeResult>> forward(String rawAddress, int limit) {
        final String q = normalize(rawAddress);
        final String cacheKey = q + ":" + limit;

        // Verificar caché primero
        GeocodeResult cached = cache.getIfPresent(cacheKey);
        if (cached != null) {
            List<GeocodeResult> cachedList = new java.util.ArrayList<>();
            cachedList.add(cached);
            return Mono.just(cachedList);
        }

        return locationIqWebClient.get()
                .uri(uri -> uri.path("/search")
                        .queryParam("key", token)
                        .queryParam("format", "json")
                        .queryParam("limit", Math.max(1, Math.min(limit, 100)))
                        .queryParam("q", q)
                        // Opcional: sesgar a Colombia
                        .queryParam("countrycodes", "co")
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                .map(list -> {
                    if (list == null || list.isEmpty()) return new java.util.ArrayList<GeocodeResult>();
                    List<GeocodeResult> results = new java.util.ArrayList<>();
                    for (Map<String, Object> f : list) {
                        double lat = Double.parseDouble(String.valueOf(f.get("lat")));
                        double lon = Double.parseDouble(String.valueOf(f.get("lon")));
                        String display = String.valueOf(f.getOrDefault("display_name", q));
                        results.add(new GeocodeResult(lat, lon, "POINT (" + lon + " " + lat + ")", display));
                    }
                    return results;
                })
                .doOnNext((List<GeocodeResult> results) -> {
                    // Guardar en caché el primer resultado si existe
                    if (!results.isEmpty()) {
                        cache.put(cacheKey, results.get(0));
                    }
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
