package co.turismo.usecase.agency;

import co.turismo.model.agency.Agency;
import co.turismo.model.agency.AgencyDashboard;
import co.turismo.model.agency.CreateAgencyRequest;
import co.turismo.model.agency.gateways.AgencyRepository;
import co.turismo.model.tourpackage.TourPackageSalesSummary;
import co.turismo.model.tourpackage.gateways.TourPackageRepository;
import co.turismo.model.user.gateways.UserRepository;
import co.turismo.model.visits.gateways.VisitGateway;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

@RequiredArgsConstructor
public class AgencyUseCase {

    private final AgencyRepository agencyRepository;
    private final UserRepository userRepository;
    private final TourPackageRepository tourPackageRepository;
    private final VisitGateway visitGateway;

    public Mono<Agency> create(String creatorEmail, CreateAgencyRequest request) {
        if (request == null) {
            return Mono.error(new IllegalArgumentException("Body requerido"));
        }
        if (request.getName() == null || request.getName().isBlank()) {
            return Mono.error(new IllegalArgumentException("name es obligatorio"));
        }

        return userRepository.findByEmail(creatorEmail)
                .switchIfEmpty(Mono.error(new IllegalStateException("Usuario no encontrado")))
                .flatMap(user -> agencyRepository.create(request)
                        .flatMap(agency -> agencyRepository.addUserToAgency(agency.getId(), user.getId())
                                .thenReturn(agency)));
    }

    public Mono<Agency> addUserToMyAgency(String requesterEmail, String userEmail) {
        if (userEmail == null || userEmail.isBlank()) {
            return Mono.error(new IllegalArgumentException("email es obligatorio"));
        }
        return agencyRepository.findByUserEmail(requesterEmail)
                .switchIfEmpty(Mono.error(new IllegalStateException("Agencia no encontrada para el usuario")))
                .zipWith(userRepository.findByEmail(userEmail)
                        .switchIfEmpty(Mono.error(new IllegalStateException("Usuario no encontrado"))))
                .flatMap(tuple -> agencyRepository.addUserToAgency(tuple.getT1().getId(), tuple.getT2().getId())
                        .thenReturn(tuple.getT1()));
    }

    public Flux<Agency> findAll() {
        return agencyRepository.findAll();
    }

    public Mono<Agency> findByUserEmail(String email) {
        if (email == null || email.isBlank()) {
            return Mono.error(new IllegalArgumentException("email es obligatorio"));
        }
        return agencyRepository.findByUserEmail(email)
                .switchIfEmpty(Mono.error(new IllegalStateException("Agencia no encontrada para el usuario")));
    }

    public Mono<AgencyDashboard> dashboard(String userEmail, LocalDate from, LocalDate to, int limit) {
        if (userEmail == null || userEmail.isBlank()) {
            return Mono.error(new IllegalArgumentException("email es obligatorio"));
        }
        if (from == null || to == null) {
            return Mono.error(new IllegalArgumentException("from y to son obligatorios"));
        }
        if (limit <= 0) {
            return Mono.error(new IllegalArgumentException("limit debe ser mayor a 0"));
        }

        return agencyRepository.findByUserEmail(userEmail)
                .switchIfEmpty(Mono.error(new IllegalStateException("Agencia no encontrada para el usuario")))
                .flatMap(agency -> Mono.zip(
                                tourPackageRepository.findByAgencyId(agency.getId()).collectList(),
                                tourPackageRepository.topSoldByAgency(agency.getId(), from, to, limit).collectList(),
                                tourPackageRepository.salesSummaryByAgency(agency.getId(), from, to)
                                        .defaultIfEmpty(TourPackageSalesSummary.builder()
                                                .totalSold(0L)
                                                .totalRevenue(0L)
                                                .build()),
                                visitGateway.topPlacesByAgency(agency.getId(), from, to, limit).collectList()
                        )
                        .map(tuple -> AgencyDashboard.builder()
                                .agency(agency)
                                .packages(tuple.getT1())
                                .topPackages(tuple.getT2())
                                .salesSummary(tuple.getT3())
                                .topPlaces(tuple.getT4())
                                .build()));
    }
}
