package co.turismo.usecase.agency;

import co.turismo.model.agency.Agency;
import co.turismo.model.agency.CreateAgencyRequest;
import co.turismo.model.agency.gateways.AgencyRepository;
import co.turismo.model.auditLog.gateways.AuditLogRepository;
import co.turismo.model.error.NotFoundException;
import co.turismo.model.tourpackage.gateways.TourPackageRepository;
import co.turismo.model.user.User;
import co.turismo.model.user.gateways.UserRepository;
import co.turismo.model.visits.gateways.VisitGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import co.turismo.model.agency.UpdateAgencyRequest;
import co.turismo.model.tourpackage.TourPackage;
import co.turismo.model.tourpackage.TourPackageSalesSummary;
import java.time.LocalDate;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgencyUseCaseTest {

    @Mock
    private AgencyRepository agencyRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TourPackageRepository tourPackageRepository;
    @Mock
    private VisitGateway visitGateway;
    @Mock
    private AuditLogRepository auditLogRepository;

    private AgencyUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new AgencyUseCase(
                agencyRepository,
                userRepository,
                tourPackageRepository,
                visitGateway,
                auditLogRepository
        );
    }

    @Test
    void createShouldFailWhenCreatorUserDoesNotExist() {
        CreateAgencyRequest request = CreateAgencyRequest.builder().name("Mi agencia").build();
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.create("owner@example.com", request))
                .expectErrorMatches(error -> error.getMessage().contains("Usuario no encontrado"))
                .verify();
    }

    @Test
    void createShouldCreateAgencyAndLinkCreatorUser() {
        User creator = User.builder().id(7L).email("owner@example.com").build();
        Agency agency = Agency.builder().id(10L).name("Turismo Sur").build();
        CreateAgencyRequest request = CreateAgencyRequest.builder().name("Turismo Sur").build();

        when(userRepository.findByEmail("owner@example.com")).thenReturn(Mono.just(creator));
        when(agencyRepository.findByEmail("owner@example.com")).thenReturn(Mono.empty());
        when(agencyRepository.create(request)).thenReturn(Mono.just(agency));
        when(agencyRepository.addUserToAgency(10L, 7L)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.create("owner@example.com", request))
                .assertNext(created -> assertEquals(10L, created.getId()))
                .verifyComplete();

        verify(agencyRepository).addUserToAgency(10L, 7L);
    }

    @Test
    void findByEmailShouldReturnNotFoundWhenAgencyDoesNotExist() {
        when(agencyRepository.findByEmail("owner@example.com")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.findByEmail("owner@example.com"))
                .expectError(NotFoundException.class)
                .verify();
    }

    @Test
    void findByUserEmailShouldFailWhenNoAgenciesAreFound() {
        when(agencyRepository.findByUserEmail("owner@example.com")).thenReturn(Flux.empty());

        StepVerifier.create(useCase.findByUserEmail("owner@example.com"))
                .expectErrorMatches(error -> error.getMessage().contains("Agencia no encontrada"))
                .verify();
    }

    // ── addUserToMyAgency ────────────────────────────────────────────────

    @Test
    void addUserToMyAgencyShouldFailWhenRequesterHasNoAgency() {
        when(agencyRepository.findByEmail("requester@example.com")).thenReturn(Mono.empty());
        // zipWith suscribe ambas fuentes en paralelo, hay que stubear la segunda
        when(userRepository.findByEmail("newuser@example.com")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.addUserToMyAgency("requester@example.com", "newuser@example.com"))
                .expectErrorMatches(e -> e.getMessage().contains("Email de la Agencia no encontrada"))
                .verify();
    }

    @Test
    void addUserToMyAgencyShouldFailWhenTargetUserDoesNotExist() {
        Agency agency = Agency.builder().id(1L).build();

        when(agencyRepository.findByEmail("requester@example.com")).thenReturn(Mono.just(agency));
        when(userRepository.findByEmail("newuser@example.com")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.addUserToMyAgency("requester@example.com", "newuser@example.com"))
                .expectError(NotFoundException.class)
                .verify();
    }

    @Test
    void addUserToMyAgencyShouldLinkUserAndReturnAgency() {
        Agency agency = Agency.builder().id(1L).build();
        User newUser = User.builder().id(5L).email("newuser@example.com").build();

        when(agencyRepository.findByEmail("requester@example.com")).thenReturn(Mono.just(agency));
        when(userRepository.findByEmail("newuser@example.com")).thenReturn(Mono.just(newUser));
        when(agencyRepository.addUserToAgency(1L, 5L)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.addUserToMyAgency("requester@example.com", "newuser@example.com"))
                .assertNext(result -> assertEquals(1L, result.getId()))
                .verifyComplete();

        verify(agencyRepository).addUserToAgency(1L, 5L);
    }

// ── findAll ──────────────────────────────────────────────────────────

    @Test
    void findAllShouldReturnAllAgencies() {
        Agency a1 = Agency.builder().id(1L).build();
        Agency a2 = Agency.builder().id(2L).build();

        when(agencyRepository.findAll()).thenReturn(Flux.just(a1, a2));

        StepVerifier.create(useCase.findAll())
                .expectNext(a1, a2)
                .verifyComplete();
    }

    @Test
    void findAllShouldReturnEmptyWhenNoAgenciesExist() {
        when(agencyRepository.findAll()).thenReturn(Flux.empty());

        StepVerifier.create(useCase.findAll())
                .verifyComplete();
    }

// ── findAllByUserEmail ───────────────────────────────────────────────

    @Test
    void findAllByUserEmailShouldReturnAgenciesForUser() {
        Agency a1 = Agency.builder().id(1L).build();
        Agency a2 = Agency.builder().id(2L).build();

        when(agencyRepository.findAllByUserEmail("user@example.com")).thenReturn(Flux.just(a1, a2));

        StepVerifier.create(useCase.findAllByUserEmail("user@example.com"))
                .expectNext(a1, a2)
                .verifyComplete();
    }

// ── update ───────────────────────────────────────────────────────────

    @Test
    void updateShouldReturnUpdatedAgency() {
        UpdateAgencyRequest request = UpdateAgencyRequest.builder().name("Nuevo nombre").build();
        Agency updated = Agency.builder().id(3L).name("Nuevo nombre").build();

        when(agencyRepository.update(3L, request)).thenReturn(Mono.just(updated));

        StepVerifier.create(useCase.update(3L, request))
                .assertNext(a -> assertEquals("Nuevo nombre", a.getName()))
                .verifyComplete();
    }

// ── delete ───────────────────────────────────────────────────────────

    @Test
    void deleteShouldDeletePackagesAndAgencyWithAuditLogs() {
        TourPackage pkg = TourPackage.builder().id(10L).build();
        Agency agency  = Agency.builder().id(5L).build();
        String[] roles = {"ROLE_ADMIN"};

        when(tourPackageRepository.findByAgencyId(5L, 100, 0)).thenReturn(Flux.just(pkg));
        when(tourPackageRepository.delete(10L)).thenReturn(Mono.empty());
        when(auditLogRepository.registrar(any())).thenReturn(Mono.empty());
        when(agencyRepository.findById(5L)).thenReturn(Mono.just(agency));
        when(agencyRepository.delete(5L)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.delete(5L, "admin@example.com", roles))
                .verifyComplete();

        verify(tourPackageRepository).delete(10L);
        verify(agencyRepository).delete(5L);
    }

    @Test
    void deleteShouldSucceedEvenWhenAgencyHasNoPackages() {
        Agency agency = Agency.builder().id(5L).build();
        String[] roles = {"ROLE_ADMIN"};

        when(tourPackageRepository.findByAgencyId(5L, 100, 0)).thenReturn(Flux.empty());
        when(agencyRepository.findById(5L)).thenReturn(Mono.just(agency));
        when(agencyRepository.delete(5L)).thenReturn(Mono.empty());
        when(auditLogRepository.registrar(any())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.delete(5L, "admin@example.com", roles))
                .verifyComplete();

        verify(tourPackageRepository, never()).delete(anyLong());
        verify(agencyRepository).delete(5L);
    }

// ── dashboard ────────────────────────────────────────────────────────

    @Test
    void dashboardShouldReturnDashboardForEachUserAgency() {
        Agency agency = Agency.builder().id(1L).build();
        LocalDate from = LocalDate.now().minusDays(30);
        LocalDate to   = LocalDate.now();

        TourPackageSalesSummary summary = TourPackageSalesSummary.builder()
                .totalSold(5L).totalRevenue(1000L).build();

        when(agencyRepository.findByUserEmail("user@example.com")).thenReturn(Flux.just(agency));
        when(tourPackageRepository.findByAgencyId(1L, 5, 0)).thenReturn(Flux.empty());
        when(tourPackageRepository.topSoldByAgency(eq(1L), any(), any(), anyInt())).thenReturn(Flux.empty());
        when(tourPackageRepository.salesSummaryByAgency(eq(1L), any(), any())).thenReturn(Mono.just(summary));
        when(visitGateway.topPlacesByAgency(eq(1L), any(), any(), anyInt())).thenReturn(Flux.empty());

        StepVerifier.create(useCase.dashboard("user@example.com", from, to, 5))
                .assertNext(dash -> {
                    assertEquals(1L, dash.getAgency().getId());
                    assertEquals(5L, dash.getSalesSummary().getTotalSold());
                })
                .verifyComplete();
    }

    @Test
    void dashboardShouldUseFallbackSummaryWhenNoSalesExist() {
        Agency agency = Agency.builder().id(1L).build();
        LocalDate from = LocalDate.now().minusDays(7);
        LocalDate to   = LocalDate.now();

        when(agencyRepository.findByUserEmail("user@example.com")).thenReturn(Flux.just(agency));
        when(tourPackageRepository.findByAgencyId(1L, 5, 0)).thenReturn(Flux.empty());
        when(tourPackageRepository.topSoldByAgency(eq(1L), any(), any(), anyInt())).thenReturn(Flux.empty());
        when(tourPackageRepository.salesSummaryByAgency(eq(1L), any(), any())).thenReturn(Mono.empty());
        when(visitGateway.topPlacesByAgency(eq(1L), any(), any(), anyInt())).thenReturn(Flux.empty());

        StepVerifier.create(useCase.dashboard("user@example.com", from, to, 5))
                .assertNext(dash -> {
                    assertEquals(0L, dash.getSalesSummary().getTotalSold());
                    assertEquals(0L, dash.getSalesSummary().getTotalRevenue());
                })
                .verifyComplete();
    }

    @Test
    void dashboardShouldFailWhenUserHasNoAgencies() {
        when(agencyRepository.findByUserEmail("user@example.com")).thenReturn(Flux.empty());

        StepVerifier.create(useCase.dashboard("user@example.com", LocalDate.now(), LocalDate.now(), 5))
                .expectErrorMatches(e -> e.getMessage().contains("Agencia no encontrada"))
                .verify();
    }

// ── updateAgencyUser ─────────────────────────────────────────────────

    @Test
    void updateAgencyUserShouldFailWhenRequesterDoesNotOwnAgency() {
        Agency agency = Agency.builder().id(2L).build();

        when(agencyRepository.findById(2L)).thenReturn(Mono.just(agency));
        when(agencyRepository.findAllByUserEmail("other@example.com")).thenReturn(Flux.empty());
        // checkAgencyOwnership emite error, pero .then() ya suscribió el siguiente paso
        when(userRepository.findByEmail("new@example.com")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.updateAgencyUser("other@example.com", 2L, 9L, "new@example.com"))
                .expectErrorMatches(e -> e.getMessage().contains("No tienes permisos"))
                .verify();
    }

    @Test
    void updateAgencyUserShouldFailWhenNewUserDoesNotExist() {
        Agency agency = Agency.builder().id(2L).build();

        when(agencyRepository.findById(2L)).thenReturn(Mono.just(agency));
        when(agencyRepository.findAllByUserEmail("owner@example.com")).thenReturn(Flux.just(agency));
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.updateAgencyUser("owner@example.com", 2L, 9L, "ghost@example.com"))
                .expectError(NotFoundException.class)
                .verify();
    }

    @Test
    void updateAgencyUserShouldReplaceUser() {
        Agency agency = Agency.builder().id(2L).build();
        User newUser  = User.builder().id(20L).email("new@example.com").build();

        when(agencyRepository.findById(2L)).thenReturn(Mono.just(agency));
        when(agencyRepository.findAllByUserEmail("owner@example.com")).thenReturn(Flux.just(agency));
        when(userRepository.findByEmail("new@example.com")).thenReturn(Mono.just(newUser));
        when(agencyRepository.updateAgencyUser(2L, 9L, 20L)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.updateAgencyUser("owner@example.com", 2L, 9L, "new@example.com"))
                .verifyComplete();

        verify(agencyRepository).updateAgencyUser(2L, 9L, 20L);
    }

// ── removeUserFromAgency ─────────────────────────────────────────────

    @Test
    void removeUserFromAgencyShouldFailWhenRequesterDoesNotOwnAgency() {
        Agency agency = Agency.builder().id(3L).build();

        when(agencyRepository.findById(3L)).thenReturn(Mono.just(agency));
        when(agencyRepository.findAllByUserEmail("stranger@example.com")).thenReturn(Flux.empty());
        // mismo motivo: .then(agencyRepository.removeUserFromAgency(...)) se evalúa antes del error
        when(agencyRepository.removeUserFromAgency(3L, 7L)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.removeUserFromAgency("stranger@example.com", 3L, 7L))
                .expectErrorMatches(e -> e.getMessage().contains("No tienes permisos"))
                .verify();
    }

    @Test
    void removeUserFromAgencyShouldSucceedForOwner() {
        Agency agency = Agency.builder().id(3L).build();

        when(agencyRepository.findById(3L)).thenReturn(Mono.just(agency));
        when(agencyRepository.findAllByUserEmail("owner@example.com")).thenReturn(Flux.just(agency));
        when(agencyRepository.removeUserFromAgency(3L, 7L)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.removeUserFromAgency("owner@example.com", 3L, 7L))
                .verifyComplete();

        verify(agencyRepository).removeUserFromAgency(3L, 7L);
    }
}

