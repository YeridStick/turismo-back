package co.turismo.usecase.user;

import co.turismo.model.error.ConflictException;
import co.turismo.model.error.NotFoundException;
import co.turismo.model.security.gateways.PasswordHasher;
import co.turismo.model.user.EmailVerificationResult;
import co.turismo.model.user.RegisterUserCommand;
import co.turismo.model.user.UpdateUserProfileRequest;
import co.turismo.model.user.User;
import co.turismo.model.user.UserInfo;
import co.turismo.model.user.gateways.UserRepository;
import co.turismo.model.user.gateways.UserVerificationGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserUseCaseTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordHasher passwordHasher;
    @Mock
    private UserVerificationGateway userVerificationGateway;

    private UserUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new UserUseCase(userRepository, passwordHasher, userVerificationGateway);
    }

    @Test
    void registerShouldSaveUserUpdatePasswordAndSendVerification() {
        RegisterUserCommand cmd = RegisterUserCommand.builder()
                .fullName("Ana")
                .email("ana@example.com")
                .identificationType("CC")
                .identificationNumber("123")
                .password("Password123")
                .build();

        User saved = User.builder().id(1L).email("ana@example.com").fullName("Ana").build();

        when(userRepository.save(any(User.class))).thenReturn(Mono.just(saved));
        when(passwordHasher.hash("Password123")).thenReturn("HASH");
        when(userRepository.updatePasswordHash("ana@example.com", "HASH")).thenReturn(Mono.empty());
        when(userVerificationGateway.sendVerificationEmail("ana@example.com"))
                .thenReturn(Mono.just(new EmailVerificationResult(EmailVerificationResult.VerificationStatus.SENT)));

        StepVerifier.create(useCase.register(cmd))
                .expectNext(saved)
                .verifyComplete();

        verify(userRepository).updatePasswordHash("ana@example.com", "HASH");
        verify(userVerificationGateway).sendVerificationEmail("ana@example.com");
    }

    @Test
    void createUserShouldMapDuplicateErrorToConflict() {
        User user = User.builder().email("ana@example.com").build();
        when(userRepository.save(user)).thenReturn(Mono.error(new RuntimeException("duplicate key")));

        StepVerifier.create(useCase.createUser(user))
                .expectError(ConflictException.class)
                .verify();
    }

    @Test
    void updateMyProfileShouldFailWhenUserDoesNotExist() {
        UpdateUserProfileRequest patch = UpdateUserProfileRequest.builder().fullName("New Name").build();
        when(userRepository.updateProfileByEmail("ana@example.com", patch)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.updateMyProfile("ana@example.com", patch))
                .expectError(NotFoundException.class)
                .verify();
    }

    @Test
    void getUserInfoShouldDefaultEmailVerifiedToFalse() {
        User user = User.builder().id(1L).email("ana@example.com").build();
        when(userRepository.findByEmail("ana@example.com")).thenReturn(Mono.just(user));
        when(userRepository.isEmailVerified("ana@example.com")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.getUserInfo("ana@example.com"))
                .assertNext(info -> {
                    assertSame(user, info.user());
                    assertFalse(info.emailVerified());
                })
                .verifyComplete();
    }

    @Test
    void setPasswordShouldReturnTrueAfterUpdatingHash() {
        when(passwordHasher.hash("Password123")).thenReturn("HASH");
        when(userRepository.updatePasswordHash("ana@example.com", "HASH")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.setPassword("ana@example.com", "Password123"))
                .expectNext(true)
                .verifyComplete();

        verify(userRepository).updatePasswordHash(eq("ana@example.com"), eq("HASH"));
    }

    @Test
    void findUsersByAgencyIdShouldReturnUsersForAgency() {
        User u1 = User.builder().id(1L).email("a@example.com").build();
        User u2 = User.builder().id(2L).email("b@example.com").build();

        when(userRepository.findByAgencyId(10L)).thenReturn(Flux.just(u1, u2));

        StepVerifier.create(useCase.findUsersByAgencyId(10L))
                .expectNext(u1, u2)
                .verifyComplete();
    }

    @Test
    void findUsersByAgencyIdShouldReturnEmptyWhenNoUsersExist() {
        when(userRepository.findByAgencyId(10L)).thenReturn(Flux.empty());

        StepVerifier.create(useCase.findUsersByAgencyId(10L))
                .verifyComplete();
    }

    @Test
    void getAllUsersShouldReturnAllUsers() {
        User u1 = User.builder().id(1L).email("a@example.com").build();
        User u2 = User.builder().id(2L).email("b@example.com").build();

        when(userRepository.findAllUser()).thenReturn(Flux.just(u1, u2));

        StepVerifier.create(useCase.getAllUsers())
                .expectNext(u1, u2)
                .verifyComplete();
    }

    @Test
    void getAllUsersShouldFailWhenNoUsersExist() {
        when(userRepository.findAllUser()).thenReturn(Flux.empty());

        StepVerifier.create(useCase.getAllUsers())
                .expectErrorMatches(e -> e.getMessage().contains("No se encontraron usuarios"))
                .verify();
    }
}

