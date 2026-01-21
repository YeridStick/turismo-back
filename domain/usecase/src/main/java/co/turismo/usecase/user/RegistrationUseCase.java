package co.turismo.usecase.user;

import co.turismo.model.user.User;
import co.turismo.model.user.gateways.UserRepository;
import co.turismo.usecase.authenticate.AccountRecoveryUseCase;
import lombok.RequiredArgsConstructor;
import co.turismo.model.security.gateways.PasswordHasher;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class RegistrationUseCase {
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final AccountRecoveryUseCase accountRecoveryUseCase;

    public Mono<User> register(RegisterUserCommand cmd) {
        if (cmd.email() == null || cmd.email().isBlank()) {
            return Mono.error(new IllegalArgumentException("Email requerido"));
        }
        if (cmd.fullName() == null || cmd.fullName().isBlank()) {
            return Mono.error(new IllegalArgumentException("Nombre requerido"));
        }

        User user = User.builder()
                .fullName(cmd.fullName())
                .email(cmd.email().trim().toLowerCase())
                .urlAvatar(cmd.urlAvatar())
                .identificationType(cmd.identificationType())
                .identificationNumber(cmd.identificationNumber())
                .build();

        return userRepository.save(user)
                .flatMap(saved -> updatePasswordIfPresent(saved.getEmail(), cmd.password())
                        .then(accountRecoveryUseCase.sendVerificationEmail(saved.getEmail()))
                        .thenReturn(saved));
    }

    private Mono<Void> updatePasswordIfPresent(String email, String password) {
        if (password == null || password.isBlank()) {
            return Mono.empty();
        }
        if (password.length() < 8) {
            return Mono.error(new IllegalArgumentException("La contrasena debe tener al menos 8 caracteres"));
        }
        String hash = passwordHasher.hash(password);
        return userRepository.updatePasswordHash(email, hash);
    }

    public record RegisterUserCommand(
            String fullName,
            String email,
            String urlAvatar,
            String identificationType,
            String identificationNumber,
            String password
    ) {}
}
