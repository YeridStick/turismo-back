package co.turismo.r2dbc.usersRepository.adapter;

import co.turismo.model.error.NotFoundException;
import co.turismo.model.user.RecoveryStatus;
import co.turismo.model.user.RecoveryTokenStatus;
import co.turismo.model.user.UpdateUserProfileRequest;
import co.turismo.model.user.User;
import co.turismo.model.user.gateways.UserRepository;
import co.turismo.r2dbc.helper.ReactiveAdapterOperations;
import co.turismo.r2dbc.usersRepository.dto.RecoveryStatusRow;
import co.turismo.r2dbc.usersRepository.dto.RecoveryTokenStatusRow;
import co.turismo.r2dbc.usersRepository.entity.UserData;
import co.turismo.r2dbc.usersRepository.repository.UserAdapterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.reactivecommons.utils.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;

@Repository
public class UserRepositoryAdapter
        extends ReactiveAdapterOperations<User, UserData, Long, UserAdapterRepository>
        implements UserRepository {

    private static final Logger LOG = LoggerFactory.getLogger(UserRepositoryAdapter.class);

    public UserRepositoryAdapter(UserAdapterRepository repository, ObjectMapper mapper) {
        super(repository, mapper, d -> mapper.map(d, User.class));
    }

    @Override
    public Mono<User> findByEmail(String email) {
        return repository.findByEmail(email)
                .map(this::toEntity)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Email no encotrado")));
    }

    @Override
    public Flux<String> findRoleNamesByEmail(String email) {
        return repository.findRoleNamesByEmail(email);
    }

    @Override
    public Mono<Boolean> isActiveByEmail(String email) {
        return repository.findByEmail(email)
                .map(u -> {
                    boolean locked = u.getLockedUntil() != null
                            && java.time.Instant.now().isBefore(u.getLockedUntil().toInstant());
                    return !locked;
                })
                .switchIfEmpty(Mono.empty());
    }

    @Override
    public Flux<String> findRoleNameByEmail(String email) {
        return repository.findRoleNamesByEmail(email);
    }

    @Override
    protected UserData toData(User entity) {
        UserData userData = super.toData(entity);

        if (userData.getOtpAttempts() == null) {
            userData.setOtpAttempts(0);
        }
        if (userData.getOtpMaxAttempts() == null) {
            userData.setOtpMaxAttempts(3);
        }
        if (userData.getCreatedAt() == null) {
            userData.setCreatedAt(OffsetDateTime.now());
        }

        return userData;
    }


    @Override
    public Mono<User> save(User user) {
        return repository.save(toData(user))
                .map(this::toEntity);
    }

    @Override
    public Flux<User> findByAgencyId(Long agencyId) {
        return repository.findByAgencyId(agencyId)
                .map(this::toEntity);
    }

    @Override
    public Mono<Void> registerOtpFail(String email){ return repository.registerOtpFail(email); }
    @Override
    public Mono<Void> registerSuccessfulLogin(String email){ return repository.registerSuccessfulLogin(email); }
    @Override
    public Mono<Void> resetLockIfExpired(String email){ return repository.resetLockIfExpired(email); }

    // ====== NUEVOS ======
    @Override
    public Mono<User> updateProfileByEmail(String email, UpdateUserProfileRequest patch) {
        return repository.updateProfileByEmail(
                        email,
                        patch.getFullName(),
                        patch.getUrlAvatar(),
                        patch.getIdentificationType(),
                        patch.getIdentificationNumber()
                )
                .map(this::toEntity);
    }

    @Override
    public Mono<User> updateEmailById(Long userId, String newEmail) {
        return repository.updateEmailById(userId, newEmail)
                .onErrorResume(DuplicateKeyException.class,
                        e -> Mono.error(new IllegalArgumentException("El correo ya está en uso")))
                .map(this::toEntity);
    }


    @Override
    public Flux<User> findAllUser() {
        return findAll();
    }

    @Override
    public Mono<Boolean> isEmailVerified(String email) {
        return repository.isEmailVerified(email);
    }

    @Override
    public Mono<Void> saveEmailVerificationToken(String email, String tokenHash, java.time.OffsetDateTime expiresAt) {
        return repository.saveEmailVerificationToken(email, tokenHash, expiresAt);
    }

    @Override
    public Mono<Boolean> verifyEmailByToken(String tokenHash) {
        return repository.verifyEmailByToken(tokenHash)
                .map(id -> true)
                .defaultIfEmpty(false);
    }

    @Override
    public Mono<Boolean> saveRecoveryCode(String email, String codeHash, java.time.OffsetDateTime expiresAt) {
        return repository.saveRecoveryCode(email, codeHash, expiresAt)
                .map(updated -> updated != null && updated > 0)
                .doOnNext(ok -> LOG.debug("saveRecoveryCode email={} updated={}", email, ok));
    }

    @Override
    public Mono<RecoveryStatus> getRecoveryStatus(String email) {
        return repository.getRecoveryStatus(email)
                .doOnNext(row -> LOG.debug(
                        "RecoveryStatusRow email={} hashPresent={} expiresAt={} attempts={}/{}",
                        email,
                        row.getRecoveryCodeHash() != null,
                        row.getRecoveryExpiresAt(),
                        row.getRecoveryAttempts(),
                        row.getRecoveryMaxAttempts()
                ))
                .map(this::toRecoveryStatus);
    }

    @Override
    public Mono<RecoveryTokenStatus> getRecoveryStatusByTokenHash(String tokenHash) {
        return repository.getRecoveryStatusByTokenHash(tokenHash)
                .doOnNext(row -> LOG.debug(
                        "RecoveryTokenStatusRow email={} hashPresent={} expiresAt={} attempts={}/{}",
                        row.getEmail(),
                        row.getRecoveryCodeHash() != null,
                        row.getRecoveryExpiresAt(),
                        row.getRecoveryAttempts(),
                        row.getRecoveryMaxAttempts()
                ))
                .map(this::toRecoveryTokenStatus);
    }

    @Override
    public Mono<Void> incrementRecoveryAttempts(String email) {
        return repository.incrementRecoveryAttempts(email);
    }

    @Override
    public Mono<Void> clearRecoveryCode(String email) {
        return repository.clearRecoveryCode(email);
    }

    @Override
    public Mono<String> getPasswordHash(String email) {
        return repository.getPasswordHash(email);
    }

    @Override
    public Mono<Void> updatePasswordHash(String email, String passwordHash) {
        return repository.findByEmail(email)
                .switchIfEmpty(Mono.error(new NotFoundException("Usuario no econtrado")))
                .flatMap(user -> repository.updatePasswordHash(email, passwordHash));
    }



    private RecoveryStatus toRecoveryStatus(RecoveryStatusRow row) {
        return new RecoveryStatus(
                row.getRecoveryCodeHash(),
                row.getRecoveryExpiresAt(),
                row.getRecoveryAttempts(),
                row.getRecoveryMaxAttempts()
        );
    }

    private RecoveryTokenStatus toRecoveryTokenStatus(RecoveryTokenStatusRow row) {
        return new RecoveryTokenStatus(
                row.getEmail(),
                row.getRecoveryExpiresAt(),
                row.getRecoveryAttempts(),
                row.getRecoveryMaxAttempts()
        );
    }
}
