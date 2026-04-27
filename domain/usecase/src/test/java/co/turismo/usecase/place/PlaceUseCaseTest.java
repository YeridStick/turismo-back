package co.turismo.usecase.place;

import co.turismo.model.error.NotFoundException;
import co.turismo.model.place.Place;
import co.turismo.model.place.gateways.PlaceRepository;
import co.turismo.model.place.strategy.PlaceSearchCriteria;
import co.turismo.model.place.strategy.PlaceSearchFactoryGateway;
import co.turismo.model.place.strategy.PlaceSearchMode;
import co.turismo.model.place.strategy.PlaceSearchStrategy;
import co.turismo.model.userIdentityPort.UserIdentityPort;
import co.turismo.model.userIdentityPort.UserSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.file.AccessDeniedException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.turismo.model.place.CreatePlaceRequest;
import co.turismo.model.place.UpdatePlaceRequest;
import co.turismo.model.place.gateways.PlaceCachePort;

import java.util.Collections;

@ExtendWith(MockitoExtension.class)
class PlaceUseCaseTest {

    @Mock
    private PlaceRepository placeRepository;
    @Mock
    private UserIdentityPort userIdentityPort;
    @Mock
    private PlaceSearchFactoryGateway placeSearchFactory;
    @Mock
    private PlaceSearchStrategy placeSearchStrategy;
    @Mock
    private PlaceCachePort placeCachePort;

    private PlaceUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new PlaceUseCase(placeRepository, userIdentityPort, placeSearchFactory, placeCachePort);
    }

    @Test
    void findByIdPlaceShouldFailWhenPlaceDoesNotExist() {
        when(placeRepository.findByPlaces(99L)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.findByIdPlace(99L))
                .expectError(NotFoundException.class)
                .verify();
    }

    @Test
    void deleteByOwnerOrAdminShouldFailWhenUserIsNotOwner() {
        Place place = Place.builder().id(5L).ownerUserId(2L).build();

        when(userIdentityPort.getUserIdForEmail("ana@example.com"))
                .thenReturn(Mono.just(new UserSummary(1L, "ana@example.com")));
        when(placeRepository.findByPlaces(5L)).thenReturn(Mono.just(place));

        StepVerifier.create(useCase.deleteByOwnerOrAdmin("ana@example.com", 5L))
                .expectError(AccessDeniedException.class)
                .verify();
    }

    @Test
    void deleteByOwnerOrAdminShouldDeleteWhenUserOwnsThePlace() {
        Place place = Place.builder().id(5L).ownerUserId(1L).build();
        Place deleted = Place.builder().id(5L).ownerUserId(1L).build();

        when(userIdentityPort.getUserIdForEmail("ana@example.com"))
                .thenReturn(Mono.just(new UserSummary(1L, "ana@example.com")));
        when(placeRepository.findByPlaces(5L)).thenReturn(Mono.just(place));
        when(placeRepository.deletePalce(5L)).thenReturn(Mono.just(deleted));
        when(placeCachePort.invalidateSearchCache()).thenReturn(Mono.empty());

        StepVerifier.create(useCase.deleteByOwnerOrAdmin("ana@example.com", 5L))
                .assertNext(result -> assertEquals(5L, result.getId()))
                .verifyComplete();

        verify(placeRepository).deletePalce(5L);
    }

    @Test
    void searchPlaceShouldDelegateToFactoryStrategy() {
        PlaceSearchCriteria criteria = PlaceSearchCriteria.builder().mode(PlaceSearchMode.ALL).page(0).size(10).build();
        Place place = Place.builder().id(1L).name("Parque").build();

        when(placeCachePort.getSearchResults(criteria)).thenReturn(Mono.empty());
        when(placeSearchFactory.getStrategy(PlaceSearchMode.ALL)).thenReturn(placeSearchStrategy);
        when(placeSearchStrategy.execute(criteria)).thenReturn(Flux.just(place));
        when(placeCachePort.saveSearchResults(criteria, Collections.singletonList(place))).thenReturn(Mono.just(true));

        StepVerifier.create(useCase.searchPlace(criteria))
                .expectNext(place)
                .verifyComplete();
    }

    @Test
    void searchPlaceTextModeShouldReturnMatchingPlaces() {
        PlaceSearchCriteria criteria = PlaceSearchCriteria.builder()
                .mode(PlaceSearchMode.TEXT)
                .q("restaurante")
                .page(0)
                .size(10)
                .build();
        Place place1 = Place.builder().id(1L).name("Restaurante La Casa").build();
        Place place2 = Place.builder().id(2L).name("Restaurante El Patio").build();
        List<Place> places = List.of(place1, place2);

        when(placeCachePort.getSearchResults(criteria)).thenReturn(Mono.empty());
        when(placeSearchFactory.getStrategy(PlaceSearchMode.TEXT)).thenReturn(placeSearchStrategy);
        when(placeSearchStrategy.execute(criteria)).thenReturn(Flux.just(place1, place2));
        when(placeCachePort.saveSearchResults(criteria, places)).thenReturn(Mono.just(true));

        StepVerifier.create(useCase.searchPlace(criteria))
                .expectNext(place1, place2)
                .verifyComplete();
    }

    @Test
    void searchPlaceNearbyModeShouldReturnNearbyPlaces() {
        PlaceSearchCriteria criteria = PlaceSearchCriteria.builder()
                .mode(PlaceSearchMode.NEARBY)
                .lat(2.936)
                .lng(-75.276)
                .radiusMeters(5000.0)
                .page(0)
                .size(20)
                .build();
        Place nearby = Place.builder().id(1L).name("Café Cercano").build();

        when(placeCachePort.getSearchResults(criteria)).thenReturn(Mono.empty());
        when(placeSearchFactory.getStrategy(PlaceSearchMode.NEARBY)).thenReturn(placeSearchStrategy);
        when(placeSearchStrategy.execute(criteria)).thenReturn(Flux.just(nearby));
        when(placeCachePort.saveSearchResults(criteria, Collections.singletonList(nearby))).thenReturn(Mono.just(true));

        StepVerifier.create(useCase.searchPlace(criteria))
                .expectNext(nearby)
                .verifyComplete();
    }

    @Test
    void searchPlaceWithPaginationShouldRespectPageAndSize() {
        PlaceSearchCriteria criteria = PlaceSearchCriteria.builder()
                .mode(PlaceSearchMode.ALL)
                .page(2)
                .size(5)
                .build();
        // Simulando página 2 con 5 elementos por página (offset 10)

        when(placeCachePort.getSearchResults(criteria)).thenReturn(Mono.empty());
        when(placeSearchFactory.getStrategy(PlaceSearchMode.ALL)).thenReturn(placeSearchStrategy);
        when(placeSearchStrategy.execute(criteria)).thenReturn(Flux.empty()); // Página vacía al final
        when(placeCachePort.saveSearchResults(criteria, Collections.emptyList())).thenReturn(Mono.just(true));

        StepVerifier.create(useCase.searchPlace(criteria))
                .verifyComplete();
    }

    // ── createPlace ──────────────────────────────────────────────────────

    @Test
    void createPlaceShouldReturnCreatedPlace() {
        CreatePlaceRequest cmd = CreatePlaceRequest.builder().name("Laguna").build();
        Place created = Place.builder().id(1L).name("Laguna").build();

        when(placeRepository.create(cmd)).thenReturn(Mono.just(created));
        when(placeCachePort.invalidateSearchCache()).thenReturn(Mono.empty());

        StepVerifier.create(useCase.createPlace(cmd))
                .assertNext(p -> assertEquals(1L, p.getId()))
                .verifyComplete();
    }

// ── verifyPlaceByAdmin ───────────────────────────────────────────────

    @Test
    void verifyPlaceByAdminShouldFailWhenAdminNotFound() {
        when(userIdentityPort.getUserIdForEmail("admin@example.com")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.verifyPlaceByAdmin("admin@example.com", 10L, true))
                .expectError(NotFoundException.class)
                .verify();
    }

    @Test
    void verifyPlaceByAdminShouldApprovePlace() {
        UserSummary admin = new UserSummary(1L, "admin@example.com");
        Place verified = Place.builder().id(10L).build();

        when(userIdentityPort.getUserIdForEmail("admin@example.com")).thenReturn(Mono.just(admin));
        when(placeRepository.verifyPlace(10L, true, true, 1L)).thenReturn(Mono.just(verified));
        when(placeCachePort.invalidateSearchCache()).thenReturn(Mono.empty());

        StepVerifier.create(useCase.verifyPlaceByAdmin("admin@example.com", 10L, true))
                .assertNext(p -> assertEquals(10L, p.getId()))
                .verifyComplete();

        verify(placeRepository).verifyPlace(10L, true, true, 1L);
    }

    @Test
    void verifyPlaceByAdminShouldRejectPlace() {
        UserSummary admin = new UserSummary(1L, "admin@example.com");
        Place rejected = Place.builder().id(10L).build();

        when(userIdentityPort.getUserIdForEmail("admin@example.com")).thenReturn(Mono.just(admin));
        when(placeRepository.verifyPlace(10L, false, false, 1L)).thenReturn(Mono.just(rejected));
        when(placeCachePort.invalidateSearchCache()).thenReturn(Mono.empty());

        StepVerifier.create(useCase.verifyPlaceByAdmin("admin@example.com", 10L, false))
                .assertNext(p -> assertEquals(10L, p.getId()))
                .verifyComplete();
    }

// ── setActiveByOwner ─────────────────────────────────────────────────

    @Test
    void setActiveByOwnerShouldReturnUpdatedPlace() {
        Place updated = Place.builder().id(3L).build();

        when(placeRepository.setActiveIfOwner("owner@example.com", 3L, true)).thenReturn(Mono.just(updated));
        when(placeCachePort.invalidateSearchCache()).thenReturn(Mono.empty());

        StepVerifier.create(useCase.setActiveByOwner("owner@example.com", 3L, true))
                .assertNext(p -> assertEquals(3L, p.getId()))
                .verifyComplete();
    }

// ── findMine ─────────────────────────────────────────────────────────

    @Test
    void findMineShouldReturnPlacesForOwner() {
        Place p1 = Place.builder().id(1L).build();
        Place p2 = Place.builder().id(2L).build();

        when(placeRepository.findPlacesByOwnerEmail("owner@example.com", 10, 0)).thenReturn(Flux.just(p1, p2));

        StepVerifier.create(useCase.findMine("owner@example.com", 10, 0))
                .expectNext(p1, p2)
                .verifyComplete();
    }

    @Test
    void findMineShouldReturnEmptyWhenOwnerHasNoPlaces() {
        when(placeRepository.findPlacesByOwnerEmail("owner@example.com", 10, 0)).thenReturn(Flux.empty());

        StepVerifier.create(useCase.findMine("owner@example.com", 10, 0))
                .verifyComplete();
    }

// ── patch ─────────────────────────────────────────────────────────────

    @Test
    void patchShouldReturnUpdatedPlace() {
        UpdatePlaceRequest req = UpdatePlaceRequest.builder().name("Nuevo nombre").build();
        Place patched = Place.builder().id(7L).name("Nuevo nombre").build();

        when(placeRepository.patch(7L, req)).thenReturn(Mono.just(patched));
        when(placeCachePort.invalidateSearchCache()).thenReturn(Mono.empty());

        StepVerifier.create(useCase.patch(7L, req))
                .assertNext(p -> assertEquals("Nuevo nombre", p.getName()))
                .verifyComplete();
    }

// ── setActive ────────────────────────────────────────────────────────

    @Test
    void setActiveShouldActivatePlace() {
        Place activated = Place.builder().id(4L).build();

        when(placeRepository.setActive(4L, true)).thenReturn(Mono.just(activated));
        when(placeCachePort.invalidateSearchCache()).thenReturn(Mono.empty());

        StepVerifier.create(useCase.setActive(4L, true))
                .assertNext(p -> assertEquals(4L, p.getId()))
                .verifyComplete();
    }

    @Test
    void setActiveShouldDeactivatePlace() {
        Place deactivated = Place.builder().id(4L).build();

        when(placeRepository.setActive(4L, false)).thenReturn(Mono.just(deactivated));
        when(placeCachePort.invalidateSearchCache()).thenReturn(Mono.empty());

        StepVerifier.create(useCase.setActive(4L, false))
                .assertNext(p -> assertEquals(4L, p.getId()))
                .verifyComplete();
    }

// ── deleteByOwnerOrAdmin — caso usuario no encontrado ────────────────

    @Test
    void deleteByOwnerOrAdminShouldFailWhenUserNotFound() {
        when(userIdentityPort.getUserIdForEmail("ghost@example.com")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.deleteByOwnerOrAdmin("ghost@example.com", 5L))
                .expectError(NotFoundException.class)
                .verify();
    }

// ── Cache Tests ──────────────────────────────────────────────────────

    @Test
    void searchPlaceCacheMissShouldQueryRepositoryAndSaveToCache() {
        PlaceSearchCriteria criteria = PlaceSearchCriteria.builder()
                .mode(PlaceSearchMode.ALL)
                .page(0)
                .size(10)
                .build();
        Place place = Place.builder().id(1L).name("New Place").build();
        List<Place> places = List.of(place);

        when(placeCachePort.getSearchResults(criteria)).thenReturn(Mono.empty());
        when(placeSearchFactory.getStrategy(PlaceSearchMode.ALL)).thenReturn(placeSearchStrategy);
        when(placeSearchStrategy.execute(criteria)).thenReturn(Flux.just(place));
        when(placeCachePort.saveSearchResults(criteria, places)).thenReturn(Mono.just(true));

        StepVerifier.create(useCase.searchPlace(criteria))
                .expectNext(place)
                .verifyComplete();

        verify(placeCachePort).saveSearchResults(criteria, places);
    }

    @Test
    void searchPlaceShouldFallbackToRepositoryWhenCacheFails() {
        PlaceSearchCriteria criteria = PlaceSearchCriteria.builder()
                .mode(PlaceSearchMode.ALL)
                .page(0)
                .size(10)
                .build();
        Place place = Place.builder().id(1L).name("Fallback Place").build();

        when(placeCachePort.getSearchResults(criteria))
                .thenReturn(Mono.error(new RuntimeException("Redis connection failed")));
        when(placeSearchFactory.getStrategy(PlaceSearchMode.ALL)).thenReturn(placeSearchStrategy);
        when(placeSearchStrategy.execute(criteria)).thenReturn(Flux.just(place));
        // Note: saveSearchResults is NOT called when cache fails - we go directly to fallback

        StepVerifier.create(useCase.searchPlace(criteria))
                .expectNext(place)
                .verifyComplete();
    }

    @Test
    void createPlaceShouldInvalidateCache() {
        CreatePlaceRequest cmd = CreatePlaceRequest.builder().name("New Place").build();
        Place created = Place.builder().id(1L).name("New Place").build();

        when(placeRepository.create(cmd)).thenReturn(Mono.just(created));
        when(placeCachePort.invalidateSearchCache()).thenReturn(Mono.empty());

        StepVerifier.create(useCase.createPlace(cmd))
                .assertNext(p -> assertEquals(1L, p.getId()))
                .verifyComplete();

        verify(placeCachePort).invalidateSearchCache();
    }

    @Test
    void createPlaceShouldSucceedEvenIfCacheInvalidationFails() {
        CreatePlaceRequest cmd = CreatePlaceRequest.builder().name("New Place").build();
        Place created = Place.builder().id(1L).name("New Place").build();

        when(placeRepository.create(cmd)).thenReturn(Mono.just(created));
        when(placeCachePort.invalidateSearchCache())
                .thenReturn(Mono.error(new RuntimeException("Redis unavailable")));

        StepVerifier.create(useCase.createPlace(cmd))
                .assertNext(p -> assertEquals(1L, p.getId()))
                .verifyComplete();
    }

    @Test
    void patchShouldInvalidateCache() {
        UpdatePlaceRequest req = UpdatePlaceRequest.builder().name("Updated").build();
        Place patched = Place.builder().id(1L).name("Updated").build();

        when(placeRepository.patch(1L, req)).thenReturn(Mono.just(patched));
        when(placeCachePort.invalidateSearchCache()).thenReturn(Mono.empty());

        StepVerifier.create(useCase.patch(1L, req))
                .assertNext(p -> assertEquals("Updated", p.getName()))
                .verifyComplete();

        verify(placeCachePort).invalidateSearchCache();
    }

    @Test
    void deleteShouldInvalidateCache() {
        Place place = Place.builder().id(5L).ownerUserId(1L).build();
        Place deleted = Place.builder().id(5L).ownerUserId(1L).build();

        when(userIdentityPort.getUserIdForEmail("ana@example.com"))
                .thenReturn(Mono.just(new UserSummary(1L, "ana@example.com")));
        when(placeRepository.findByPlaces(5L)).thenReturn(Mono.just(place));
        when(placeRepository.deletePalce(5L)).thenReturn(Mono.just(deleted));
        when(placeCachePort.invalidateSearchCache()).thenReturn(Mono.empty());

        StepVerifier.create(useCase.deleteByOwnerOrAdmin("ana@example.com", 5L))
                .assertNext(result -> assertEquals(5L, result.getId()))
                .verifyComplete();

        verify(placeCachePort).invalidateSearchCache();
    }
}

