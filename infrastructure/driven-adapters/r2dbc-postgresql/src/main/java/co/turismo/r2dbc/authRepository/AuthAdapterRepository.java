package co.turismo.r2dbc.authRepository;

import co.turismo.r2dbc.usersRepository.entity.UserData;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface AuthAdapterRepository extends ReactiveCrudRepository<UserData, Long> {

    @Query("""
        SELECT totp_secret_encrypted
          FROM users
         WHERE lower(email) = lower(:email)
        """)
    Mono<String> getTotpSecretByEmail(@Param("email") String email);

    @Query("""
        SELECT COALESCE(totp_enabled, FALSE)
          FROM users
         WHERE lower(email) = lower(:email)
        """)
    Mono<Boolean> isTotpEnabledByEmail(@Param("email") String email);

    @Modifying
    @Query("""
        UPDATE users
           SET totp_secret_encrypted = :secret,
               totp_enabled = FALSE
         WHERE lower(email) = lower(:email)
        """)
    Mono<Integer> saveSecretDraft(@Param("email") String email,
                                  @Param("secret") String secret);

    @Modifying
    @Query("""
        UPDATE users
           SET totp_enabled = TRUE
         WHERE lower(email) = lower(:email)
        """)
    Mono<Integer> enableTotp(@Param("email") String email);
}

