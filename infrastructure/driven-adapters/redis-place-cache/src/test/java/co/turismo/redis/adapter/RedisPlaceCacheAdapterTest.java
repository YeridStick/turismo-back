package co.turismo.redis.adapter;

import co.turismo.model.place.Place;
import co.turismo.model.place.strategy.PlaceSearchCriteria;
import co.turismo.model.place.strategy.PlaceSearchMode;
import co.turismo.redis.config.PlaceCacheProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.mockito.MockitoAnnotations;
import org.junit.jupiter.api.BeforeAll;

@ExtendWith(MockitoExtension.class)
class RedisPlaceCacheAdapterTest {

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    @Mock
    private PlaceCacheProperties properties;

    private RedisPlaceCacheAdapter adapter;

    @BeforeEach
    void setUp() {
        lenient().when(properties.getKeyPrefix()).thenReturn("places");
        lenient().when(properties.isEnabled()).thenReturn(true);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        adapter = new RedisPlaceCacheAdapter(redisTemplate, properties);
    }

    @Test
    void getSearchResultsShouldReturnEmptyWhenCacheDisabled() {
        lenient().when(properties.isEnabled()).thenReturn(false);

        PlaceSearchCriteria criteria = PlaceSearchCriteria.builder()
                .mode(PlaceSearchMode.ALL)
                .page(0)
                .size(10)
                .build();

        StepVerifier.create(adapter.getSearchResults(criteria))
                .verifyComplete();
    }

    @Test
    void getSearchResultsShouldReturnCachedPlaces() throws JsonProcessingException {
        Place place = Place.builder().id(1L).name("Test Place").build();
        List<Place> places = Collections.singletonList(place);
        String json = new ObjectMapper().writeValueAsString(places);

        when(valueOperations.get(anyString())).thenReturn(Mono.just(json));

        PlaceSearchCriteria criteria = PlaceSearchCriteria.builder()
                .mode(PlaceSearchMode.ALL)
                .page(0)
                .size(10)
                .build();

        StepVerifier.create(adapter.getSearchResults(criteria))
                .assertNext(result -> {
                    assert result.size() == 1;
                    assert result.get(0).getId().equals(1L);
                    assert result.get(0).getName().equals("Test Place");
                })
                .verifyComplete();
    }

    @Test
    void getSearchResultsShouldReturnEmptyWhenCacheMiss() {
        when(valueOperations.get(anyString())).thenReturn(Mono.empty());

        PlaceSearchCriteria criteria = PlaceSearchCriteria.builder()
                .mode(PlaceSearchMode.ALL)
                .page(0)
                .size(10)
                .build();

        StepVerifier.create(adapter.getSearchResults(criteria))
                .verifyComplete();
    }

    @Test
    void getSearchResultsShouldFallbackOnError() {
        when(valueOperations.get(anyString())).thenReturn(Mono.error(new RuntimeException("Redis error")));

        PlaceSearchCriteria criteria = PlaceSearchCriteria.builder()
                .mode(PlaceSearchMode.ALL)
                .page(0)
                .size(10)
                .build();

        StepVerifier.create(adapter.getSearchResults(criteria))
                .verifyComplete();
    }

    @Test
    void saveSearchResultsShouldReturnFalseWhenCacheDisabled() {
        lenient().when(properties.isEnabled()).thenReturn(false);

        PlaceSearchCriteria criteria = PlaceSearchCriteria.builder()
                .mode(PlaceSearchMode.ALL)
                .page(0)
                .size(10)
                .build();
        List<Place> places = Collections.emptyList();

        StepVerifier.create(adapter.saveSearchResults(criteria, places))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void saveSearchResultsShouldSaveToRedis() {
        when(properties.getSearchTtl()).thenReturn(Duration.ofSeconds(60));
        when(valueOperations.set(anyString(), anyString(), any(Duration.class))).thenReturn(Mono.just(true));

        PlaceSearchCriteria criteria = PlaceSearchCriteria.builder()
                .mode(PlaceSearchMode.ALL)
                .page(0)
                .size(10)
                .build();
        Place place = Place.builder().id(1L).name("Test").build();
        List<Place> places = Collections.singletonList(place);

        StepVerifier.create(adapter.saveSearchResults(criteria, places))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void saveSearchResultsShouldReturnFalseOnError() {
        when(properties.getSearchTtl()).thenReturn(Duration.ofSeconds(60));
        when(valueOperations.set(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Mono.error(new RuntimeException("Redis error")));

        PlaceSearchCriteria criteria = PlaceSearchCriteria.builder()
                .mode(PlaceSearchMode.ALL)
                .page(0)
                .size(10)
                .build();
        Place place = Place.builder().id(1L).name("Test").build();
        List<Place> places = Collections.singletonList(place);

        StepVerifier.create(adapter.saveSearchResults(criteria, places))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void invalidateSearchCacheShouldDeleteMatchingKeys() {
        when(redisTemplate.keys("places:search:*")).thenReturn(Flux.just("places:search:mode=all", "places:search:q=test"));
        when(redisTemplate.delete(any(String[].class))).thenReturn(Mono.just(2L));

        StepVerifier.create(adapter.invalidateSearchCache())
                .verifyComplete();
    }

    @Test
    void invalidateSearchCacheShouldCompleteWhenNoKeysFound() {
        when(redisTemplate.keys("places:search:*")).thenReturn(Flux.empty());

        StepVerifier.create(adapter.invalidateSearchCache())
                .verifyComplete();
    }

    @Test
    void invalidateSearchCacheShouldHandleError() {
        when(redisTemplate.keys("places:search:*")).thenReturn(Flux.error(new RuntimeException("Redis error")));

        StepVerifier.create(adapter.invalidateSearchCache())
                .verifyComplete();
    }

    @Test
    void invalidatePlaceDetailShouldDeleteKey() {
        when(redisTemplate.delete("places:detail:123")).thenReturn(Mono.just(1L));

        StepVerifier.create(adapter.invalidatePlaceDetail(123L))
                .verifyComplete();
    }

    @Test
    void invalidatePlaceDetailShouldHandleNull() {
        StepVerifier.create(adapter.invalidatePlaceDetail(null))
                .verifyComplete();
    }

    @Test
    void buildSearchKeyShouldGenerateNonEmptyKey() {
        PlaceSearchCriteria criteria = PlaceSearchCriteria.builder()
                .mode(PlaceSearchMode.TEXT)
                .q("San Agustin")
                .categoryId(5L)
                .lat(2.536)
                .lng(-75.276)
                .radiusMeters(5000.0)
                .onlyNearby(true)
                .page(1)
                .size(20)
                .build();

        String key = adapter.buildSearchKey(criteria);

        // Verify key is not null/empty and contains expected parts
        org.junit.jupiter.api.Assertions.assertNotNull(key);
        org.junit.jupiter.api.Assertions.assertFalse(key.isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(key.startsWith("places:search"));
    }

    @Test
    void buildSearchKeyShouldHandleMinimalCriteria() {
        PlaceSearchCriteria criteria = PlaceSearchCriteria.builder()
                .mode(PlaceSearchMode.ALL)
                .page(0)
                .size(10)
                .build();

        String key = adapter.buildSearchKey(criteria);

        // Verify minimal key structure
        org.junit.jupiter.api.Assertions.assertNotNull(key);
        org.junit.jupiter.api.Assertions.assertTrue(key.contains("mode=all"));
        org.junit.jupiter.api.Assertions.assertTrue(key.contains("page=0"));
        org.junit.jupiter.api.Assertions.assertTrue(key.contains("size=10"));
    }
}
