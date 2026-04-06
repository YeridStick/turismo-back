package co.turismo.r2dbc.usersRepository.repository;

import co.turismo.r2dbc.usersRepository.dto.RecoveryStatusRow;
import co.turismo.r2dbc.usersRepository.dto.RecoveryTokenStatusRow;
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
        SELECT u.*
          FROM users u
          JOIN agency_users au ON au.user_id = u.id
         WHERE au.agency_id = :agencyId
    """)
    Flux<UserData> findByAgencyId(@Param("agencyId") Long agencyId);

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

    @Query("""
        UPDATE users
           SET email = :newEmail
         WHERE id = :userId
     RETURNING id, full_name, email, url_avatar, identification_type, identification_number,
               otp_hash, otp_expires_at, otp_attempts, otp_max_attempts,
               locked_until, last_login_at, created_at
    """)
    Mono<UserData> updateEmailById(@Param("userId") Long userId, @Param("newEmail") String newEmail);

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

    @Query("""
        SELECT COALESCE(email_verified, FALSE)
          FROM users
         WHERE lower(email)=lower(:email)
    """)
    Mono<Boolean> isEmailVerified(@Param("email") String email);

    @Query("""
        UPDATE users
           SET email_verification_token_hash = :tokenHash,
               email_verification_expires_at = :expiresAt,
               email_verified = FALSE
         WHERE lower(email)=lower(:email)
    """)
    Mono<Void> saveEmailVerificationToken(@Param("email") String email,
                                          @Param("tokenHash") String tokenHash,
                                          @Param("expiresAt") java.time.OffsetDateTime expiresAt);

    @Query("""
        UPDATE users
           SET email_verified = TRUE,
               email_verification_token_hash = NULL,
               email_verification_expires_at = NULL
         WHERE email_verification_token_hash = :tokenHash
           AND email_verification_expires_at > NOW()
     RETURNING id
    """)
    Mono<Long> verifyEmailByToken(@Param("tokenHash") String tokenHash);

    @Query("""
        UPDATE users
           SET recovery_code_hash = :codeHash,
               recovery_expires_at = :expiresAt,
               recovery_attempts = 0
         WHERE lower(trim(email))=lower(trim(:email))
    """)
    Mono<Integer> saveRecoveryCode(@Param("email") String email,
                                   @Param("codeHash") String codeHash,
                                   @Param("expiresAt") java.time.OffsetDateTime expiresAt);

    @Query("""
        SELECT recovery_code_hash AS recoveryCodeHash,
               recovery_expires_at AS recoveryExpiresAt,
               recovery_attempts AS recoveryAttempts,
               recovery_max_attempts AS recoveryMaxAttempts
           FROM users
          WHERE lower(trim(email))=lower(trim(:email))
    """)
    Mono<RecoveryStatusRow> getRecoveryStatus(@Param("email") String email);

    @Query("""
        SELECT email,
               recovery_code_hash,
               recovery_expires_at,
               recovery_attempts,
               recovery_max_attempts
          FROM users
         WHERE recovery_code_hash = :tokenHash
           AND recovery_expires_at > NOW()
    """)
    Mono<RecoveryTokenStatusRow> getRecoveryStatusByTokenHash(@Param("tokenHash") String tokenHash);

    @Query("""
        UPDATE users
           SET recovery_attempts = recovery_attempts + 1
         WHERE lower(trim(email))=lower(trim(:email))
    """)
    Mono<Void> incrementRecoveryAttempts(@Param("email") String email);

    @Query("""
        UPDATE users
           SET recovery_code_hash = NULL,
               recovery_expires_at = NULL,
               recovery_attempts = 0
         WHERE lower(trim(email))=lower(trim(:email))
    """)
    Mono<Void> clearRecoveryCode(@Param("email") String email);

    @Query("""
        SELECT password_hash
          FROM users
         WHERE lower(email)=lower(:email)
    """)
    Mono<String> getPasswordHash(@Param("email") String email);

    @Query("""
        UPDATE users
           SET password_hash = :passwordHash
         WHERE lower(email)=lower(:email)
    """)
    Mono<Void> updatePasswordHash(@Param("email") String email,
                                  @Param("passwordHash") String passwordHash);
}
