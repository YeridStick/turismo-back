package co.turismo.r2dbc.authRepository.adapter;

import co.turismo.model.authenticationsession.gateways.TotpSecretRepository;
import co.turismo.r2dbc.authRepository.AuthAdapterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class TotpSecretAdapter implements TotpSecretRepository {

    private final AuthAdapterRepository repo;

    @Override
    public Mono<String> getTotpSecretByEmail(String email) {
        return repo.getTotpSecretByEmail(email);
    }

    @Override
    public Mono<Boolean> isTotpEnabledByEmail(String email) {
        return repo.isTotpEnabledByEmail(email);
    }

    @Override
    public Mono<Void> saveSecretDraft(String email, String base32Secret) {
        return repo.saveSecretDraft(email, base32Secret).then();
    }

    @Override
    public Mono<Void> enableTotp(String email) {
        return repo.enableTotp(email).then();
    }
}
