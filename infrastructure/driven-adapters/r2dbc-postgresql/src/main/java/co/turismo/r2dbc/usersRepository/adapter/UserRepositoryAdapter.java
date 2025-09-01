package co.turismo.r2dbc.usersRepository.adapter;

import co.turismo.model.user.User;
import co.turismo.model.user.gateways.UserRepository;
import co.turismo.r2dbc.helper.ReactiveAdapterOperations;
import co.turismo.r2dbc.usersRepository.entity.UserData;
import co.turismo.r2dbc.usersRepository.repository.UserAdapterRepository;
import org.reactivecommons.utils.ObjectMapper;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;

@Repository
public class UserRepositoryAdapter
        extends ReactiveAdapterOperations<User, UserData, Long, UserAdapterRepository>
        implements UserRepository {

    public UserRepositoryAdapter(UserAdapterRepository repository, ObjectMapper mapper) {
        super(repository, mapper, d -> mapper.map(d, User.class));
    }

    @Override
    public Mono<User> findByEmail(String email) {
        return repository.findByEmail(email)
                .map(this::toEntity);
    }

    @Override
    public Flux<String> findRoleNamesByEmail(String email) {
        return repository.findRoleNamesByEmail(email);
    }

    @Override
    public Mono<Boolean> isActiveByEmail(String email) {
        return repository.findByEmail(email)
                .map(u -> u.getLockedUntil() == null || OffsetDateTime.now().isAfter(u.getLockedUntil()))
                .defaultIfEmpty(false);
    }

    @Override
    public Flux<String> findRoleNameByEmail(String email) {
        return repository.findRoleNamesByEmail(email);
    }

    @Override
    public Mono<User> save(User user) {
        return Mono.just(user)
                .map(this::toData)
                .flatMap(repository::save)
                .map(this::toEntity);
    }

    @Override
    public Mono<Void> registerOtpFail(String email){
        return repository.registerOtpFail(email);
    }
    @Override
    public Mono<Void> registerSuccessfulLogin(String email){
        return repository.registerSuccessfulLogin(email);
    }
    @Override public Mono<Void> resetLockIfExpired(String email){
        return repository.resetLockIfExpired(email);
    }

}
