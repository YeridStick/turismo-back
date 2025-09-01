package co.turismo.r2dbc.usersRepository.repository;

import co.turismo.r2dbc.usersRepository.entity.UserData;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.query.ReactiveQueryByExampleExecutor;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UserAdapterRepository extends ReactiveCrudRepository<UserData, Long>,
        ReactiveQueryByExampleExecutor<UserData> {
    @Query("SELECT * FROM users WHERE lower(email)=lower(:email) LIMIT 1")
    Mono<UserData> findByEmail(@Param("email") String email);

    @Query("""
        SELECT r.role_name
          FROM users u
          JOIN user_roles ur ON ur.user_id = u.id
          JOIN roles r       ON r.id = ur.role_id
         WHERE lower(u.email)=lower(:email)
    """)
    Flux<String> findRoleNamesByEmail(@Param("email") String email);

    // ↑ OTP fallido: suma intento y bloquea si llegó al máximo
    @Query("""
        UPDATE users
           SET otp_attempts = otp_attempts + 1,
               locked_until = CASE
                   WHEN otp_attempts + 1 >= otp_max_attempts
                   THEN NOW() + INTERVAL '15 minutes'
                   ELSE locked_until
               END
         WHERE lower(email)=lower(:email)
    """)
    Mono<Void> registerOtpFail(@Param("email") String email);

    // ↑ Login exitoso: marca último acceso y limpia estados
    @Query("""
        UPDATE users
           SET last_login_at = NOW(),
               otp_attempts = 0,
               locked_until = NULL
         WHERE lower(email)=lower(:email)
    """)
    Mono<Void> registerSuccessfulLogin(@Param("email") String email);

    // ↑ Si el bloqueo ya venció, limpia contador y bloqueo
    @Query("""
        UPDATE users
           SET otp_attempts = 0,
               locked_until = NULL
         WHERE lower(email)=lower(:email)
           AND locked_until IS NOT NULL
           AND locked_until < NOW()
    """)
    Mono<Void> resetLockIfExpired(@Param("email") String email);
}
