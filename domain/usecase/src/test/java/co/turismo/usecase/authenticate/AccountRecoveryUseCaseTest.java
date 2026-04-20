package co.turismo.usecase.authenticate;

import co.turismo.model.authenticationsession.gateways.TotpSecretRepository;
import co.turismo.model.common.AppUrlConfig;
import co.turismo.model.notification.EmailMessage;
import co.turismo.model.notification.gateways.EmailGateway;
import co.turismo.model.security.gateways.PasswordHasher;
import co.turismo.model.user.RecoveryTokenStatus;
import co.turismo.model.user.gateways.UserRepository;
import co.turismo.model.user.gateways.UserVerificationGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.OffsetDateTime;

import co.turismo.model.user.EmailVerificationResult;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountRecoveryUseCaseTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private TotpSecretRepository totpSecretRepository;
    @Mock
    private EmailGateway emailGateway;
    @Mock
    private PasswordHasher passwordHasher;
    @Mock
    private UserVerificationGateway userVerificationGateway;

    private AccountRecoveryUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new AccountRecoveryUseCase(
                userRepository,
                totpSecretRepository,
                emailGateway,
                new AppUrlConfig("http://localhost:8082", "https://frontend.turismo.com"),
                passwordHasher,
                userVerificationGateway
        );
    }

    @Test
    void verifyEmailTokenShouldFailWhenTokenIsBlank() {
        StepVerifier.create(useCase.verifyEmailToken("   "))
                .expectErrorMatches(error -> error.getMessage().contains("Token requerido"))
                .verify();
    }

    @Test
    void verifyEmailTokenShouldFailWhenTokenCannotBeValidated() {
        when(userRepository.verifyEmailByToken(anyString())).thenReturn(Mono.just(false));

        StepVerifier.create(useCase.verifyEmailToken("token-invalido"))
                .expectErrorMatches(error -> error.getMessage().contains("Token"))
                .verify();
    }

    @Test
    void createRecoveryTokenShouldPersistAndReturnToken() {
        when(userRepository.saveRecoveryCode(eq("ana@example.com"), anyString(), any()))
                .thenReturn(Mono.just(true));

        StepVerifier.create(useCase.createRecoveryToken(" Ana@Example.com "))
                .assertNext(token -> assertTrue(token != null && !token.isBlank()))
                .verifyComplete();
    }

    @Test
    void requestRecoveryCodeShouldSendEmail() {
        when(userRepository.saveRecoveryCode(eq("ana@example.com"), anyString(), any()))
                .thenReturn(Mono.just(true));
        when(emailGateway.sendEmail(any(EmailMessage.class))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.requestRecoveryCode("ana@example.com"))
                .verifyComplete();

        ArgumentCaptor<EmailMessage> mailCaptor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailGateway).sendEmail(mailCaptor.capture());
        assertEquals("ana@example.com", mailCaptor.getValue().to());
        assertTrue(mailCaptor.getValue().htmlBody().contains("/recover-account?token="));
    }

    @Test
    void confirmRecoveryCodeShouldUpdatePasswordResetTotpAndClearCode() {
        RecoveryTokenStatus tokenStatus = new RecoveryTokenStatus(
                "ana@example.com",
                OffsetDateTime.now().plusMinutes(5),
                0,
                3
        );

        when(userRepository.getRecoveryStatusByTokenHash(anyString())).thenReturn(Mono.just(tokenStatus));
        when(passwordHasher.hash("Password123")).thenReturn("HASHED");
        when(userRepository.updatePasswordHash("ana@example.com", "HASHED")).thenReturn(Mono.empty());
        when(totpSecretRepository.resetTotp("ana@example.com")).thenReturn(Mono.empty());
        when(userRepository.clearRecoveryCode("ana@example.com")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.confirmRecoveryCode("token-ok", "Password123"))
                .verifyComplete();

        verify(userRepository).updatePasswordHash("ana@example.com", "HASHED");
        verify(totpSecretRepository).resetTotp("ana@example.com");
        verify(userRepository).clearRecoveryCode("ana@example.com");
    }


    // ── sendVerificationEmail ────────────────────────────────────────────

    @Test
    void sendVerificationEmailShouldCompleteWhenGatewaySucceeds() {
        when(userVerificationGateway.sendVerificationEmail("ana@example.com"))
                .thenReturn(Mono.just(new EmailVerificationResult(EmailVerificationResult.VerificationStatus.SENT)));

        StepVerifier.create(useCase.sendVerificationEmail("ana@example.com"))
                .verifyComplete();
    }

// ── requestEmailVerification ─────────────────────────────────────────

    @Test
    void requestEmailVerificationShouldReturnVerificationResult() {
        EmailVerificationResult result =
                new EmailVerificationResult(EmailVerificationResult.VerificationStatus.SENT);

        when(userVerificationGateway.sendVerificationEmail("ana@example.com"))
                .thenReturn(Mono.just(result));

        StepVerifier.create(useCase.requestEmailVerification("ana@example.com"))
                .assertNext(r -> assertEquals(EmailVerificationResult.VerificationStatus.SENT, r.status()))
                .verifyComplete();
    }

// ── verifyEmailToken ─────────────────────────────────────────────────

    @Test
    void verifyEmailTokenShouldCompleteWhenTokenIsValid() {
        when(userRepository.verifyEmailByToken(anyString())).thenReturn(Mono.just(true));

        StepVerifier.create(useCase.verifyEmailToken("token-valido"))
                .verifyComplete();
    }

    @Test
    void verifyEmailTokenShouldFailWhenTokenIsNull() {
        StepVerifier.create(useCase.verifyEmailToken(null))
                .expectErrorMatches(e -> e.getMessage().contains("Token requerido"))
                .verify();
    }

// ── createRecoveryToken ──────────────────────────────────────────────

    @Test
    void createRecoveryTokenShouldFailWhenEmailIsBlank() {
        StepVerifier.create(useCase.createRecoveryToken("   "))
                .expectErrorMatches(e -> e.getMessage().contains("Email requerido"))
                .verify();
    }

    @Test
    void createRecoveryTokenShouldFailWhenRepositoryReturnsFalse() {
        when(userRepository.saveRecoveryCode(eq("ana@example.com"), anyString(), any()))
                .thenReturn(Mono.just(false));

        StepVerifier.create(useCase.createRecoveryToken("ana@example.com"))
                .expectErrorMatches(e -> e.getMessage().contains("No fue posible guardar"))
                .verify();
    }

// ── saveRecoveryToken ────────────────────────────────────────────────

    @Test
    void saveRecoveryTokenShouldCompleteWhenTokenIsSaved() {
        when(userRepository.saveRecoveryCode(eq("ana@example.com"), anyString(), any()))
                .thenReturn(Mono.just(true));

        StepVerifier.create(useCase.saveRecoveryToken("ana@example.com", "mi-token"))
                .verifyComplete();

        verify(userRepository).saveRecoveryCode(eq("ana@example.com"), anyString(), any());
    }

    @Test
    void saveRecoveryTokenShouldFailWhenEmailIsBlank() {
        StepVerifier.create(useCase.saveRecoveryToken("  ", "mi-token"))
                .expectErrorMatches(e -> e.getMessage().contains("Email y token requeridos"))
                .verify();
    }

    @Test
    void saveRecoveryTokenShouldFailWhenTokenIsBlank() {
        StepVerifier.create(useCase.saveRecoveryToken("ana@example.com", "  "))
                .expectErrorMatches(e -> e.getMessage().contains("Email y token requeridos"))
                .verify();
    }

    @Test
    void saveRecoveryTokenShouldFailWhenRepositoryReturnsFalse() {
        when(userRepository.saveRecoveryCode(eq("ana@example.com"), anyString(), any()))
                .thenReturn(Mono.just(false));

        StepVerifier.create(useCase.saveRecoveryToken("ana@example.com", "mi-token"))
                .expectErrorMatches(e -> e.getMessage().contains("No fue posible guardar"))
                .verify();
    }

// ── confirmRecoveryCode — validaciones de input ──────────────────────

    @Test
    void confirmRecoveryCodeShouldFailWhenTokenIsBlank() {
        StepVerifier.create(useCase.confirmRecoveryCode("  ", "Password123"))
                .expectErrorMatches(e -> e.getMessage().contains("Token requerido"))
                .verify();
    }

    @Test
    void confirmRecoveryCodeShouldFailWhenPasswordIsBlank() {
        StepVerifier.create(useCase.confirmRecoveryCode("token-ok", "  "))
                .expectErrorMatches(e -> e.getMessage().contains("contrasena es requerida"))
                .verify();
    }

    @Test
    void confirmRecoveryCodeShouldFailWhenPasswordIsTooShort() {
        StepVerifier.create(useCase.confirmRecoveryCode("token-ok", "abc"))
                .expectErrorMatches(e -> e.getMessage().contains("al menos 8 caracteres"))
                .verify();
    }

    @Test
    void confirmRecoveryCodeShouldFailWhenTokenNotFound() {
        when(userRepository.getRecoveryStatusByTokenHash(anyString())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.confirmRecoveryCode("token-inexistente", "Password123"))
                .expectErrorMatches(e -> e.getMessage().contains("Token invalido o expirado"))
                .verify();
    }

    @Test
    void confirmRecoveryCodeShouldFailWhenAttemptsExceeded() {
        RecoveryTokenStatus tokenStatus = new RecoveryTokenStatus(
                "ana@example.com",
                OffsetDateTime.now().plusMinutes(5),
                3,
                3
        );

        when(userRepository.getRecoveryStatusByTokenHash(anyString())).thenReturn(Mono.just(tokenStatus));
        when(passwordHasher.hash("Password123")).thenReturn("HASHED");
        when(userRepository.updatePasswordHash("ana@example.com", "HASHED")).thenReturn(Mono.empty());
        when(totpSecretRepository.resetTotp("ana@example.com")).thenReturn(Mono.empty());
        when(userRepository.clearRecoveryCode("ana@example.com")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.confirmRecoveryCode("token-agotado", "Password123"))
                .expectErrorMatches(e -> e.getMessage().contains("intentos permitidos"))
                .verify();
    }

    @Test
    void confirmRecoveryCodeShouldFailWhenExpiresAtIsNull() {
        RecoveryTokenStatus tokenStatus = new RecoveryTokenStatus(
                "ana@example.com",
                null,
                0,
                3
        );

        when(userRepository.getRecoveryStatusByTokenHash(anyString())).thenReturn(Mono.just(tokenStatus));
        when(passwordHasher.hash("Password123")).thenReturn("HASHED");
        when(userRepository.updatePasswordHash("ana@example.com", "HASHED")).thenReturn(Mono.empty());
        when(totpSecretRepository.resetTotp("ana@example.com")).thenReturn(Mono.empty());
        when(userRepository.clearRecoveryCode("ana@example.com")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.confirmRecoveryCode("token-sin-fecha", "Password123"))
                .expectErrorMatches(e -> e.getMessage().contains("Token inválido o ya usado"))
                .verify();
    }

// ── generateRecoveryToken ────────────────────────────────────────────

    @Test
    void generateRecoveryTokenShouldReturnNonBlankString() {
        String token = useCase.generateRecoveryToken();
        assertTrue(token != null && !token.isBlank());
    }

// ── requestRecoveryCode — validación de email ────────────────────────

    @Test
    void requestRecoveryCodeShouldFailWhenEmailIsBlank() {
        StepVerifier.create(useCase.requestRecoveryCode("  "))
                .expectErrorMatches(e -> e.getMessage().contains("Email requerido"))
                .verify();
    }

// ── fix: test existente con stubs innecesarios ───────────────────────

    @Test
    void confirmRecoveryCodeShouldFailWhenTokenIsExpired() {
        RecoveryTokenStatus tokenStatus = new RecoveryTokenStatus(
                "ana@example.com",
                OffsetDateTime.now().minusMinutes(1),
                0,
                3
        );

        when(userRepository.getRecoveryStatusByTokenHash(anyString())).thenReturn(Mono.just(tokenStatus));
        when(passwordHasher.hash("Password123")).thenReturn("HASHED");
        when(userRepository.updatePasswordHash("ana@example.com", "HASHED")).thenReturn(Mono.empty());
        when(totpSecretRepository.resetTotp("ana@example.com")).thenReturn(Mono.empty());
        when(userRepository.clearRecoveryCode("ana@example.com")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.confirmRecoveryCode("token-exp", "Password123"))
                .expectErrorMatches(e -> e.getMessage().contains("Token expirado"))
                .verify();
    }
}
