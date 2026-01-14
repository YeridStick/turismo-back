package co.turismo.usecase.agency;

import co.turismo.model.agency.Agency;
import co.turismo.model.agency.CreateAgencyRequest;
import co.turismo.model.agency.gateways.AgencyRepository;
import co.turismo.model.user.gateways.UserRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class AgencyUseCase {

    private final AgencyRepository agencyRepository;
    private final UserRepository userRepository;

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
}
