package co.turismo.usecase.authenticate;

import co.turismo.model.authenticationsession.gateways.AuthenticationSessionRepository;
import co.turismo.model.authenticationsession.gateways.TotpSecretRepository;
import co.turismo.model.authenticationsession.gateways.TotpVerifier;
import co.turismo.model.security.gateways.PasswordHasher;
import co.turismo.model.user.gateways.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Set;
import java.util.function.Supplier;

import co.turismo.model.user.User;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticateUseCaseTest {

    @Mock
    private AuthenticationSessionRepository authenticationRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TotpSecretRepository totpSecretRepository;
    @Mock
    private TotpVerifier totpVerifier;
    @Mock
    private PasswordHasher passwordHasher;

    private AuthenticateUseCase useCase;

    @BeforeEach
    void setUp() {
        Supplier<String> secretSupplier = () -> "BASE32SECRET";
        useCase = new AuthenticateUseCase(
                authenticationRepository,
                userRepository,
                totpSecretRepository,
                totpVerifier,
                passwordHasher,
                secretSupplier
        );
    }

    @Test
    void setupTotpShouldGenerateSecretWhenCredentialsAreValid() {
        when(userRepository.isActiveByEmail("ana@example.com")).thenReturn(Mono.just(true));
        when(userRepository.getPasswordHash("ana@example.com")).thenReturn(Mono.just("HASH"));
        when(passwordHasher.matches("Pass123", "HASH")).thenReturn(true);
        when(totpSecretRepository.isTotpEnabledByEmail("ana@example.com")).thenReturn(Mono.just(false));
        when(totpSecretRepository.saveSecretDraft("ana@example.com", "BASE32SECRET")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.setupTotp(" Ana@Example.com ", "Pass123"))
                .assertNext(response -> {
                    assertEquals("BASE32SECRET", response.secretBase32());
                    assertTrue(response.otpAuthUri().contains("BASE32SECRET"));
                    assertTrue(response.otpAuthUri().contains("ana%40example.com"));
                })
                .verifyComplete();

        verify(totpSecretRepository).saveSecretDraft("ana@example.com", "BASE32SECRET");
    }

    @Test
    void setupTotpShouldRegisterOtpFailWhenPasswordIsInvalid() {
        when(userRepository.isActiveByEmail("ana@example.com")).thenReturn(Mono.just(true));
        when(userRepository.getPasswordHash("ana@example.com")).thenReturn(Mono.just("HASH"));
        when(passwordHasher.matches("wrong-pass", "HASH")).thenReturn(false);
        when(userRepository.registerOtpFail("ana@example.com")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.setupTotp("ana@example.com", "wrong-pass"))
                .expectErrorMatches(error -> error.getMessage().contains("Credenciales"))
                .verify();

        verify(userRepository).registerOtpFail("ana@example.com");
    }

    @Test
    void authenticatePasswordShouldFallbackToVisitorRoleWhenUserHasNoRoles() {
        when(userRepository.isActiveByEmail("ana@example.com")).thenReturn(Mono.just(true));
        when(userRepository.getPasswordHash("ana@example.com")).thenReturn(Mono.just("HASH"));
        when(passwordHasher.matches("Pass123", "HASH")).thenReturn(true);
        when(userRepository.registerSuccessfulLogin("ana@example.com")).thenReturn(Mono.empty());
        when(userRepository.findRoleNamesByEmail("ana@example.com")).thenReturn(Flux.empty());
        when(authenticationRepository.generateToken(eq("ana@example.com"), anySet(), eq("10.0.0.1")))
                .thenReturn(Mono.just("jwt-token"));

        StepVerifier.create(useCase.authenticatePassword("ana@example.com", "Pass123", "10.0.0.1"))
                .expectNext("jwt-token")
                .verifyComplete();

        ArgumentCaptor<Set<String>> rolesCaptor = ArgumentCaptor.forClass(Set.class);
        verify(authenticationRepository).generateToken(eq("ana@example.com"), rolesCaptor.capture(), eq("10.0.0.1"));
        assertTrue(rolesCaptor.getValue().contains("VISITOR"));
    }

    @Test
    void confirmTotpShouldFailWhenCodeIsInvalid() {
        when(userRepository.isActiveByEmail("ana@example.com")).thenReturn(Mono.just(true));
        when(totpSecretRepository.getTotpSecretByEmail("ana@example.com")).thenReturn(Mono.just("SECRET"));
        when(totpVerifier.verify("SECRET", 123456, 2)).thenReturn(Mono.just(false));
        when(userRepository.registerOtpFail("ana@example.com")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.confirmTotp("ana@example.com", "123456"))
                .expectErrorMatches(error -> error.getMessage().contains("TOTP"))
                .verify();

        verify(userRepository).registerOtpFail("ana@example.com");
    }

    @Test
    void logoutSessionShouldDelegateToRepository() {
        when(authenticationRepository.revokeToken("token-1")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.logoutSession("token-1"))
                .verifyComplete();

        verify(authenticationRepository).revokeToken("token-1");
    }

    @Test
    void validateSessionShouldDelegateToRepository() {
        when(authenticationRepository.validateToken("token-1", "127.0.0.1")).thenReturn(Mono.just(true));

        StepVerifier.create(useCase.validateSession("token-1", "127.0.0.1"))
                .expectNext(true)
                .verifyComplete();
    }

    // ── setupTotp ────────────────────────────────────────────────────────

    @Test
    void setupTotpShouldFailWhenUserIsInactive() {
        when(userRepository.isActiveByEmail("ana@example.com")).thenReturn(Mono.just(false));
        when(userRepository.getPasswordHash("ana@example.com")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.setupTotp("ana@example.com", "Pass123"))
                .expectErrorMatches(e -> e.getMessage().contains("inexistente o bloqueado"))
                .verify();
    }

    @Test
    void setupTotpShouldFailWhenTotpAlreadyEnabled() {
        when(userRepository.isActiveByEmail("ana@example.com")).thenReturn(Mono.just(true));
        when(userRepository.getPasswordHash("ana@example.com")).thenReturn(Mono.just("HASH"));
        when(passwordHasher.matches("Pass123", "HASH")).thenReturn(true);
        when(totpSecretRepository.isTotpEnabledByEmail("ana@example.com")).thenReturn(Mono.just(true));

        StepVerifier.create(useCase.setupTotp("ana@example.com", "Pass123"))
                .expectErrorMatches(e -> e.getMessage().contains("TOTP ya habilitado"))
                .verify();
    }

    @Test
    void setupTotpShouldFailWhenPasswordHashNotConfigured() {
        when(userRepository.isActiveByEmail("ana@example.com")).thenReturn(Mono.just(true));
        when(userRepository.getPasswordHash("ana@example.com")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.setupTotp("ana@example.com", "Pass123"))
                .expectErrorMatches(e -> e.getMessage().contains("Password no configurado"))
                .verify();
    }

// ── totpStatus ───────────────────────────────────────────────────────

    @Test
    void totpStatusShouldReturnTrueWhenTotpIsEnabled() {
        User user = User.builder().id(1L).email("ana@example.com").lockedUntil(null).build();

        when(userRepository.findByEmail("ana@example.com")).thenReturn(Mono.just(user));
        when(totpSecretRepository.isTotpEnabledByEmail("ana@example.com")).thenReturn(Mono.just(true));

        StepVerifier.create(useCase.totpStatus("ana@example.com"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void totpStatusShouldReturnFalseWhenTotpIsNotEnabled() {
        User user = User.builder().id(1L).email("ana@example.com").lockedUntil(null).build();

        when(userRepository.findByEmail("ana@example.com")).thenReturn(Mono.just(user));
        when(totpSecretRepository.isTotpEnabledByEmail("ana@example.com")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.totpStatus("ana@example.com"))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void totpStatusShouldFailWhenUserNotFound() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.totpStatus("ghost@example.com"))
                .expectErrorMatches(e -> e.getMessage().contains("Usuario no encontrado"))
                .verify();
    }

    @Test
    void totpStatusShouldFailWhenUserIsLocked() {
        User user = User.builder()
                .id(1L)
                .email("ana@example.com")
                .lockedUntil(java.time.OffsetDateTime.now().plusMinutes(10))
                .build();

        when(userRepository.findByEmail("ana@example.com")).thenReturn(Mono.just(user));

        StepVerifier.create(useCase.totpStatus("ana@example.com"))
                .expectErrorMatches(e -> e.getMessage().contains("bloqueado hasta"))
                .verify();
    }

// ── confirmTotp ──────────────────────────────────────────────────────

    @Test
    void confirmTotpShouldFailWhenUserIsInactive() {
        when(userRepository.isActiveByEmail("ana@example.com")).thenReturn(Mono.just(false));
        when(totpSecretRepository.getTotpSecretByEmail("ana@example.com")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.confirmTotp("ana@example.com", "123456"))
                .expectErrorMatches(e -> e.getMessage().contains("inexistente o bloqueado"))
                .verify();
    }

    @Test
    void confirmTotpShouldFailWhenSecretNotFound() {
        when(userRepository.isActiveByEmail("ana@example.com")).thenReturn(Mono.just(true));
        when(totpSecretRepository.getTotpSecretByEmail("ana@example.com")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.confirmTotp("ana@example.com", "123456"))
                .expectErrorMatches(e -> e.getMessage().contains("Secreto TOTP no encontrado"))
                .verify();
    }

    @Test
    void confirmTotpShouldEnableTotpWhenCodeIsValid() {
        when(userRepository.isActiveByEmail("ana@example.com")).thenReturn(Mono.just(true));
        when(totpSecretRepository.getTotpSecretByEmail("ana@example.com")).thenReturn(Mono.just("SECRET"));
        when(totpVerifier.verify("SECRET", 123456, 2)).thenReturn(Mono.just(true));
        when(totpSecretRepository.enableTotp("ana@example.com")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.confirmTotp("ana@example.com", "123456"))
                .verifyComplete();

        verify(totpSecretRepository).enableTotp("ana@example.com");
    }

// ── authenticateTotp ─────────────────────────────────────────────────

    @Test
    void authenticateTotpShouldReturnTokenWhenCodeIsValid() {
        when(userRepository.isActiveByEmail("ana@example.com")).thenReturn(Mono.just(true));
        when(totpSecretRepository.isTotpEnabledByEmail("ana@example.com")).thenReturn(Mono.just(true));
        when(totpSecretRepository.getTotpSecretByEmail("ana@example.com")).thenReturn(Mono.just("SECRET"));
        when(totpVerifier.verify("SECRET", 123456, 2)).thenReturn(Mono.just(true));
        when(userRepository.registerSuccessfulLogin("ana@example.com")).thenReturn(Mono.empty());
        when(userRepository.findRoleNamesByEmail("ana@example.com")).thenReturn(Flux.just("ROLE_USER"));
        when(authenticationRepository.generateToken(eq("ana@example.com"), anySet(), eq("10.0.0.1")))
                .thenReturn(Mono.just("jwt-token"));

        StepVerifier.create(useCase.authenticateTotp("ana@example.com", "123456", "10.0.0.1"))
                .expectNext("jwt-token")
                .verifyComplete();
    }

    @Test
    void authenticateTotpShouldFailWhenUserIsInactive() {
        when(userRepository.isActiveByEmail("ana@example.com")).thenReturn(Mono.just(false));
        when(totpSecretRepository.isTotpEnabledByEmail("ana@example.com")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.authenticateTotp("ana@example.com", "123456", "10.0.0.1"))
                .expectErrorMatches(e -> e.getMessage().contains("inexistente o bloqueado"))
                .verify();
    }

    @Test
    void authenticateTotpShouldFailWhenTotpNotEnabled() {
        when(userRepository.isActiveByEmail("ana@example.com")).thenReturn(Mono.just(true));
        when(totpSecretRepository.isTotpEnabledByEmail("ana@example.com")).thenReturn(Mono.just(false));

        StepVerifier.create(useCase.authenticateTotp("ana@example.com", "123456", "10.0.0.1"))
                .expectErrorMatches(e -> e.getMessage().contains("TOTP no habilitado"))
                .verify();
    }

    @Test
    void authenticateTotpShouldFailAndRegisterOtpFailWhenCodeIsInvalid() {
        when(userRepository.isActiveByEmail("ana@example.com")).thenReturn(Mono.just(true));
        when(totpSecretRepository.isTotpEnabledByEmail("ana@example.com")).thenReturn(Mono.just(true));
        when(totpSecretRepository.getTotpSecretByEmail("ana@example.com")).thenReturn(Mono.just("SECRET"));
        when(totpVerifier.verify("SECRET", 999999, 2)).thenReturn(Mono.just(false));
        when(userRepository.registerOtpFail("ana@example.com")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.authenticateTotp("ana@example.com", "999999", "10.0.0.1"))
                .expectErrorMatches(e -> e.getMessage().contains("Código TOTP inválido"))
                .verify();

        verify(userRepository).registerOtpFail("ana@example.com");
    }

// ── authenticatePassword ─────────────────────────────────────────────

    @Test
    void authenticatePasswordShouldFailWhenUserIsInactive() {
        when(userRepository.isActiveByEmail("ana@example.com")).thenReturn(Mono.just(false));
        when(userRepository.getPasswordHash("ana@example.com")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.authenticatePassword("ana@example.com", "Pass123", "10.0.0.1"))
                .expectErrorMatches(e -> e.getMessage().contains("inexistente o bloqueado"))
                .verify();
    }

    @Test
    void authenticatePasswordShouldFailAndRegisterOtpFailWhenPasswordIsWrong() {
        when(userRepository.isActiveByEmail("ana@example.com")).thenReturn(Mono.just(true));
        when(userRepository.getPasswordHash("ana@example.com")).thenReturn(Mono.just("HASH"));
        when(passwordHasher.matches("wrong", "HASH")).thenReturn(false);
        when(userRepository.registerOtpFail("ana@example.com")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.authenticatePassword("ana@example.com", "wrong", "10.0.0.1"))
                .expectErrorMatches(e -> e.getMessage().contains("Credenciales inválidas"))
                .verify();

        verify(userRepository).registerOtpFail("ana@example.com");
    }

    @Test
    void authenticatePasswordShouldReturnTokenWithRolesWhenCredentialsAreValid() {
        when(userRepository.isActiveByEmail("ana@example.com")).thenReturn(Mono.just(true));
        when(userRepository.getPasswordHash("ana@example.com")).thenReturn(Mono.just("HASH"));
        when(passwordHasher.matches("Pass123", "HASH")).thenReturn(true);
        when(userRepository.registerSuccessfulLogin("ana@example.com")).thenReturn(Mono.empty());
        when(userRepository.findRoleNamesByEmail("ana@example.com")).thenReturn(Flux.just("ROLE_ADMIN"));
        when(authenticationRepository.generateToken(eq("ana@example.com"), anySet(), eq("10.0.0.1")))
                .thenReturn(Mono.just("jwt-token"));

        StepVerifier.create(useCase.authenticatePassword("ana@example.com", "Pass123", "10.0.0.1"))
                .expectNext("jwt-token")
                .verifyComplete();

        ArgumentCaptor<Set<String>> rolesCaptor = ArgumentCaptor.forClass(Set.class);
        verify(authenticationRepository).generateToken(eq("ana@example.com"), rolesCaptor.capture(), eq("10.0.0.1"));
        assertTrue(rolesCaptor.getValue().contains("ROLE_ADMIN"));
    }

// ── refreshSession ───────────────────────────────────────────────────

    @Test
    void refreshSessionShouldDelegateToRepository() {
        when(authenticationRepository.refreshToken("old-token", "10.0.0.1"))
                .thenReturn(Mono.just("new-token"));

        StepVerifier.create(useCase.refreshSession("old-token", "10.0.0.1"))
                .expectNext("new-token")
                .verifyComplete();

        verify(authenticationRepository).refreshToken("old-token", "10.0.0.1");
    }
}

