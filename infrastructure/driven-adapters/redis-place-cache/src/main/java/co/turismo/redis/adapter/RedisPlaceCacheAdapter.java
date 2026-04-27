package co.turismo.redis.adapter;

import co.turismo.model.place.Place;
import co.turismo.model.place.gateways.PlaceCachePort;
import co.turismo.model.place.strategy.PlaceSearchCriteria;
import co.turismo.model.place.strategy.PlaceSearchMode;
import co.turismo.redis.config.PlaceCacheProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class RedisPlaceCacheAdapter implements PlaceCachePort {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final PlaceCacheProperties properties;
    private final ObjectMapper objectMapper;
    private final TypeReference<List<Place>> placeListType;

    public RedisPlaceCacheAdapter(ReactiveStringRedisTemplate redisTemplate, PlaceCacheProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.placeListType = new TypeReference<List<Place>>() {};
    }

    @Override
    public Mono<List<Place>> getSearchResults(PlaceSearchCriteria criteria) {
        if (!properties.isEnabled()) {
            return Mono.empty();
        }

        String key = buildSearchKey(criteria);

        return redisTemplate.opsForValue()
                .get(key)
                .flatMap(json -> {
                    try {
                        List<Place> places = objectMapper.readValue(json, placeListType);
                        log.info("CACHE HIT key={}", key);
                        return Mono.just(places);
                    } catch (JsonProcessingException e) {
                        log.warn("CACHE ERROR operation=deserialize key={} message={}", key, e.getMessage());
                        return Mono.empty();
                    }
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.debug("CACHE MISS key={}", key);
                    return Mono.empty();
                }))
                .onErrorResume(e -> {
                    log.warn("CACHE ERROR operation=get key={} message={}", key, e.getMessage());
                    return Mono.empty();
                });
    }

    @Override
    public Mono<Boolean> saveSearchResults(PlaceSearchCriteria criteria, List<Place> places) {
        if (!properties.isEnabled()) {
            return Mono.just(false);
        }

        String key = buildSearchKey(criteria);

        try {
            String json = objectMapper.writeValueAsString(places);
            Duration ttl = properties.getSearchTtl();

            return redisTemplate.opsForValue()
                    .set(key, json, ttl)
                    .doOnSuccess(success -> {
                        if (Boolean.TRUE.equals(success)) {
                            log.info("CACHE SET key={} ttl={}s places={}", key, ttl.getSeconds(), places.size());
                        }
                    })
                    .onErrorResume(e -> {
                        log.warn("CACHE ERROR operation=set key={} message={}", key, e.getMessage());
                        return Mono.just(false);
                    });
        } catch (JsonProcessingException e) {
            log.warn("CACHE ERROR operation=serialize key={} message={}", key, e.getMessage());
            return Mono.just(false);
        }
    }

    @Override
    public Mono<Void> invalidateSearchCache() {
        if (!properties.isEnabled()) {
            return Mono.empty();
        }

        String pattern = buildKey("search:*");
        String searchPattern = properties.getKeyPrefix() + ":search:*";

        log.info("CACHE INVALIDATE pattern={}", searchPattern);

        return redisTemplate.keys(pattern)
                .collectList()
                .flatMap(keys -> {
                    if (keys.isEmpty()) {
                        log.debug("CACHE INVALIDATE no keys found for pattern={}", searchPattern);
                        return Mono.empty().then();
                    }
                    String[] keysArray = keys.toArray(new String[0]);
                    return redisTemplate.delete(keysArray)
                            .doOnSuccess(count -> log.info("CACHE INVALIDATE deleted={} keys for pattern={}", count, searchPattern))
                            .then();
                })
                .onErrorResume(e -> {
                    log.warn("CACHE ERROR operation=invalidate message={}", e.getMessage());
                    return Mono.empty();
                })
                .then();
    }

    @Override
    public Mono<Void> invalidatePlaceDetail(Long placeId) {
        if (!properties.isEnabled() || placeId == null) {
            return Mono.empty();
        }

        String key = buildKey("detail:" + placeId);

        return redisTemplate.delete(key)
                .doOnSuccess(count -> {
                    if (count > 0) {
                        log.info("CACHE INVALIDATE key={}", key);
                    }
                })
                .onErrorResume(e -> {
                    log.warn("CACHE ERROR operation=invalidate-detail key={} message={}", key, e.getMessage());
                    return Mono.empty();
                })
                .then();
    }

    String buildSearchKey(PlaceSearchCriteria criteria) {
        StringBuilder sb = new StringBuilder();
        sb.append(properties.getKeyPrefix()).append(":search");

        // Modo de búsqueda
        PlaceSearchMode mode = criteria.getMode();
        if (mode != null) {
            sb.append(":mode=").append(mode.name().toLowerCase());
        }

        // Query de texto (normalizada)
        if (criteria.getQ() != null && !criteria.getQ().trim().isEmpty()) {
            String normalizedQuery = normalizeForKey(criteria.getQ());
            sb.append(":q=").append(normalizedQuery);
        }

        // Categoría
        if (criteria.getCategoryId() != null) {
            sb.append(":categoryId=").append(criteria.getCategoryId());
        }

        // Geolocalización (solo si es modo NEARBY o tiene coordenadas)
        if (criteria.getLat() != null && criteria.getLng() != null) {
            sb.append(":lat=").append(formatCoordinate(criteria.getLat()))
              .append(":lng=").append(formatCoordinate(criteria.getLng()));

            if (criteria.getRadiusMeters() != null) {
                sb.append(":radius=").append(criteria.getRadiusMeters().intValue());
            }

            if (criteria.isOnlyNearby()) {
                sb.append(":onlyNearby=true");
            }
        }

        // Paginación siempre incluida
        sb.append(":page=").append(criteria.getPage());
        sb.append(":size=").append(criteria.getSize());

        return sb.toString();
    }

    String buildKey(String suffix) {
        return properties.getKeyPrefix() + ":" + suffix;
    }

    String normalizeForKey(String value) {
        String normalized = value.toLowerCase()
                .trim()
                .replaceAll("\\s+", "-")
                .replaceAll("[^a-z0-9-]", "");
        return normalized.substring(0, Math.min(50, normalized.length()));
    }

    String formatCoordinate(Double value) {
        return String.format(java.util.Locale.US, "%.6f", value);
    }
}
