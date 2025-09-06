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

    @Query("""
        UPDATE users
           SET full_name = COALESCE(:fullName, full_name),
               url_avatar = COALESCE(:urlAvatar, url_avatar),
               identification_type = COALESCE(:identificationType, identification_type),
               identification_number = COALESCE(:identificationNumber, identification_number)
         WHERE lower(email)=lower(:email)
     RETURNING id, full_name, email, url_avatar, identification_type, identification_number,
               otp_hash, otp_expires_at, otp_attempts, otp_max_attempts,
               locked_until, last_login_at, created_at
    """)
    Mono<UserData> updateProfileByEmail(
            @Param("email") String email,
            @Param("fullName") String fullName,
            @Param("urlAvatar") String urlAvatar,
            @Param("identificationType") String identificationType,
            @Param("identificationNumber") String identificationNumber
    );

    // Para confirmar cambio de correo tras OTP verificado (opcional)
    @Query("""
        UPDATE users
           SET email = :newEmail
         WHERE id = :userId
     RETURNING id, full_name, email, url_avatar, identification_type, identification_number,
               otp_hash, otp_expires_at, otp_attempts, otp_max_attempts,
               locked_until, last_login_at, created_at
    """)
    Mono<UserData> updateEmailById(@Param("userId") Long userId, @Param("newEmail") String newEmail);

    // ======= (tus métodos OTP existentes) =======
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

    @Query("""
        UPDATE users
           SET last_login_at = NOW(),
               otp_attempts = 0,
               locked_until = NULL
         WHERE lower(email)=lower(:email)
    """)
    Mono<Void> registerSuccessfulLogin(@Param("email") String email);

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