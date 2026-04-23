package co.turismo.usecase.tourpackage;

import co.turismo.model.agency.Agency;
import co.turismo.model.agency.gateways.AgencyRepository;
import co.turismo.model.auditLog.gateways.AuditLogRepository;
import co.turismo.model.error.ConflictException;
import co.turismo.model.error.NotFoundException;
import co.turismo.model.place.Place;
import co.turismo.model.place.gateways.PlaceRepository;
import co.turismo.model.tourpackage.CreateTourPackageRequest;
import co.turismo.model.tourpackage.TourPackage;
import co.turismo.model.tourpackage.UpdateTourPackageRequest;
import co.turismo.model.tourpackage.gateways.TourPackageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TourPackageUseCaseTest {

    @Mock
    private TourPackageRepository tourPackageRepository;
    @Mock
    private AgencyRepository agencyRepository;
    @Mock
    private PlaceRepository placeRepository;
    @Mock
    private AuditLogRepository auditLogRepository;

    private TourPackageUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new TourPackageUseCase(
                tourPackageRepository,
                agencyRepository,
                placeRepository,
                auditLogRepository
        );
    }

    @Test
    void createShouldFailWhenAnyPlaceDoesNotExist() {
        CreateTourPackageRequest request = CreateTourPackageRequest.builder()
                .title("Plan Huila")
                .placeIds(new Long[]{1L, 2L})
                .build();

        when(agencyRepository.findByEmail("agency@example.com"))
                .thenReturn(Mono.just(Agency.builder().id(10L).build()));
        when(placeRepository.findByIds(any(Long[].class), anyInt(), anyInt()))
                .thenReturn(Flux.just(Place.builder().id(1L).build()));
        when(tourPackageRepository.create(any(CreateTourPackageRequest.class)))
                .thenReturn(Mono.just(TourPackage.builder().agencyId(10L).placeIds(new Long[]{1L, 2L}).build()));

        StepVerifier.create(useCase.create("agency@example.com", request))
                .expectError(ConflictException.class)
                .verify();
    }

    @Test
    void createShouldNormalizePlaceIdsAndAttachPlaces() {
        CreateTourPackageRequest request = CreateTourPackageRequest.builder()
                .title("Plan Huila")
                .placeIds(new Long[]{1L, 1L, 2L, null, -3L})
                .build();

        Place p1 = Place.builder().id(1L).build();
        Place p2 = Place.builder().id(2L).build();

        when(agencyRepository.findByEmail("agency@example.com"))
                .thenReturn(Mono.just(Agency.builder().id(10L).build()));
        when(placeRepository.findByIds(any(Long[].class), anyInt(), anyInt()))
                .thenReturn(Flux.just(p1, p2), Flux.just(p1, p2));
        when(tourPackageRepository.create(any(CreateTourPackageRequest.class)))
                .thenReturn(Mono.just(TourPackage.builder()
                        .agencyId(10L)
                        .placeIds(new Long[]{1L, 2L})
                        .build()));

        StepVerifier.create(useCase.create("agency@example.com", request))
                .assertNext(pkg -> {
                    assertEquals(10L, pkg.getAgencyId());
                    assertEquals(2, pkg.getPlaces().size());
                })
                .verifyComplete();

        ArgumentCaptor<CreateTourPackageRequest> captor = ArgumentCaptor.forClass(CreateTourPackageRequest.class);
        verify(tourPackageRepository).create(captor.capture());
        assertArrayEquals(new Long[]{1L, 2L}, captor.getValue().getPlaceIds());
    }

    @Test
    void findByIdShouldFailWhenPackageDoesNotExist() {
        when(tourPackageRepository.findById(99L)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.findById(99L, 10, 0))
                .expectError(NotFoundException.class)
                .verify();
    }

    @Test
    void updateShouldNormalizePlaceIdsAndAttachPlaces() {
        UpdateTourPackageRequest request = UpdateTourPackageRequest.builder()
                .title("Nuevo titulo")
                .placeIds(new Long[]{2L, 2L, 3L, null, -1L})
                .build();

        Place p2 = Place.builder().id(2L).build();
        Place p3 = Place.builder().id(3L).build();

        when(placeRepository.findByIds(any(Long[].class), anyInt(), anyInt()))
                .thenReturn(Flux.just(p2, p3), Flux.just(p2, p3));
        when(tourPackageRepository.update(eq(7L), any(UpdateTourPackageRequest.class)))
                .thenReturn(Mono.just(TourPackage.builder()
                        .id(7L)
                        .placeIds(new Long[]{2L, 3L})
                        .build()));

        StepVerifier.create(useCase.update(7L, request))
                .assertNext(pkg -> {
                    List<Long> ids = Arrays.asList(pkg.getPlaceIds());
                    assertEquals(List.of(2L, 3L), ids);
                    assertEquals(2, pkg.getPlaces().size());
                })
                .verifyComplete();

        ArgumentCaptor<UpdateTourPackageRequest> captor = ArgumentCaptor.forClass(UpdateTourPackageRequest.class);
        verify(tourPackageRepository).update(eq(7L), captor.capture());
        assertArrayEquals(new Long[]{2L, 3L}, captor.getValue().getPlaceIds());
    }

    @Test
    void createShouldFailWhenAgencyDoesNotExist() {
        CreateTourPackageRequest request = CreateTourPackageRequest.builder()
                .title("Plan Huila")
                .placeIds(new Long[]{1L})
                .build();

        when(agencyRepository.findByEmail("agency@example.com"))
                .thenReturn(Mono.empty());

        StepVerifier.create(useCase.create("agency@example.com", request))
                .expectError(NotFoundException.class)
                .verify();
    }

    @Test
    void createShouldReturnCreatedPackageWhenCreatedHasNoId() {
        CreateTourPackageRequest request = CreateTourPackageRequest.builder()
                .title("Plan Huila")
                .placeIds(new Long[]{1L})
                .build();

        Agency agency = Agency.builder().id(10L).build();
        Place p1 = Place.builder().id(1L).build();

        TourPackage created = TourPackage.builder()
                .agencyId(10L)
                .placeIds(new Long[]{1L})
                .build(); // sin id

        when(agencyRepository.findByEmail("agency@example.com"))
                .thenReturn(Mono.just(agency));
        when(placeRepository.findByIds(any(Long[].class), anyInt(), anyInt()))
                .thenReturn(Flux.just(p1), Flux.just(p1));
        when(tourPackageRepository.create(any(CreateTourPackageRequest.class)))
                .thenReturn(Mono.just(created));

        StepVerifier.create(useCase.create("agency@example.com", request))
                .assertNext(pkg -> {
                    assertEquals(10L, pkg.getAgencyId());
                    assertEquals(1, pkg.getPlaces().size());
                })
                .verifyComplete();
    }

    @Test
    void findAllShouldAttachPlaces() {
        TourPackage pkg = TourPackage.builder()
                .id(1L)
                .placeIds(new Long[]{1L, 2L})
                .build();

        when(tourPackageRepository.findAll(10, 0))
                .thenReturn(Flux.just(pkg));

        StepVerifier.create(useCase.findAll(10, 0))
                .assertNext(result -> {
                    assertEquals(1L, result.getId());
                    assertEquals(2, result.getPlaceIds().length);
                })
                .verifyComplete();
    }

    @Test
    void findByAgencyIdShouldAttachPlaces() {
        TourPackage pkg = TourPackage.builder()
                .id(1L)
                .agencyId(10L)
                .placeIds(new Long[]{1L})
                .build();

        when(tourPackageRepository.findByAgencyId(10L, null, null))
                .thenReturn(Flux.just(pkg));

        StepVerifier.create(useCase.findByAgencyId(10L, null, null))
                .assertNext(result -> {
                    assertEquals(10L, result.getAgencyId());
                    assertEquals(1, result.getPlaceIds().length);
                })
                .verifyComplete();
    }

    @Test
    void findByIdShouldReturnPackageWithPlaces() {
        TourPackage pkg = TourPackage.builder()
                .id(5L)
                .placeIds(new Long[]{1L})
                .build();

        Place p1 = Place.builder().id(1L).build();

        when(tourPackageRepository.findById(5L))
                .thenReturn(Mono.just(pkg));
        when(placeRepository.findByIds(any(Long[].class), anyInt(), anyInt()))
                .thenReturn(Flux.just(p1));

        StepVerifier.create(useCase.findById(5L, 10, 0))
                .assertNext(result -> {
                    assertEquals(5L, result.getId());
                    assertEquals(1, result.getPlaces().size());
                })
                .verifyComplete();
    }

    @Test
    void updateShouldSkipPlacesValidationWhenPlaceIdsIsNull() {
        UpdateTourPackageRequest request = UpdateTourPackageRequest.builder()
                .title("Nuevo titulo")
                .placeIds(null)
                .build();

        TourPackage updated = TourPackage.builder()
                .id(7L)
                .placeIds(null)
                .build();

        when(tourPackageRepository.update(eq(7L), any(UpdateTourPackageRequest.class)))
                .thenReturn(Mono.just(updated));

        StepVerifier.create(useCase.update(7L, request))
                .assertNext(pkg -> assertEquals(0, pkg.getPlaces().size()))
                .verifyComplete();

        verify(tourPackageRepository).update(eq(7L), any(UpdateTourPackageRequest.class));
    }

    @Test
    void deleteShouldCompleteSuccessfully() {
        when(tourPackageRepository.delete(7L)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.delete(7L))
                .verifyComplete();

        verify(tourPackageRepository).delete(7L);
    }

    @Test
    void deleteWithAuditShouldDeleteAndRegisterAuditLog() {
        TourPackage pkg = TourPackage.builder()
                .id(7L)
                .title("Plan Huila")
                .build();

        when(tourPackageRepository.findById(7L)).thenReturn(Mono.just(pkg));
        when(tourPackageRepository.delete(7L)).thenReturn(Mono.empty());
        when(auditLogRepository.registrar(any())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.delete(7L, "user@test.com", new String[]{"ADMIN"}))
                .verifyComplete();

        verify(tourPackageRepository).delete(7L);
        verify(auditLogRepository).registrar(any());
    }

    @Test
    void findByIdShouldReturnEmptyPlacesWhenPackageHasNoPlaceIds() {
        TourPackage pkg = TourPackage.builder()
                .id(5L)
                .placeIds(null)
                .build();

        when(tourPackageRepository.findById(5L))
                .thenReturn(Mono.just(pkg));

        StepVerifier.create(useCase.findById(5L, 10, 0))
                .assertNext(result -> assertEquals(0, result.getPlaces().size()))
                .verifyComplete();
    }
}
