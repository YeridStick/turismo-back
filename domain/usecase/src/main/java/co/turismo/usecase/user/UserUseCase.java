package co.turismo.usecase.user;
 
import co.turismo.model.error.ConflictException;
import co.turismo.model.error.DuplicateKeyException;
import co.turismo.model.error.NotFoundException;
import co.turismo.model.user.RegisterUserCommand;
import co.turismo.model.user.UpdateUserProfileRequest;
import co.turismo.model.user.User;
import co.turismo.model.user.UserInfo;
import co.turismo.model.user.gateways.UserRepository;
import co.turismo.model.user.gateways.UserVerificationGateway;
import co.turismo.model.security.gateways.PasswordHasher;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
 
 
@RequiredArgsConstructor
public class UserUseCase {
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final UserVerificationGateway userVerificationGateway;
 
    public Mono<User> createUser(User user) {
        return userRepository.save(user)
                .onErrorMap(e -> e.getMessage().contains("duplicate key") || e instanceof DuplicateKeyException,
                        e -> new ConflictException("Ya existe un usuario con ese email"));
    }
 
    public Mono<User> register(RegisterUserCommand cmd) {
        return userRepository.save(cmd.toUser())
                .onErrorMap(DuplicateKeyException.class, e ->
                        new ConflictException("Ya existe un usuario con el email: " + cmd.getEmail()))
                .flatMap(saved -> updatePasswordIfPresent(saved.getEmail(), cmd.getPassword())
                        .then(userVerificationGateway.sendVerificationEmail(saved.getEmail()))
                        .thenReturn(saved));
    }
 
    private Mono<Void> updatePasswordIfPresent(String email, String password) {
        String hash = passwordHasher.hash(password);
        return userRepository.updatePasswordHash(email, hash);
    }
 
    public Mono<User> updateMyProfile(String email, UpdateUserProfileRequest patch) {
        return userRepository.updateProfileByEmail(email, patch)
                .switchIfEmpty(Mono.error(new NotFoundException("Usuario no encontrado: " + email)));
    }
 
    /** Para cuando YA hayas verificado OTP en tu flujo de autenticación. */
    public Mono<User> confirmEmailChange(Long userId, String newEmail) {
        return userRepository.updateEmailById(userId, newEmail)
                .switchIfEmpty(Mono.error(new NotFoundException("Usuario no encontrado con id: " + userId)))
                .onErrorMap(DuplicateKeyException.class, e ->
                        new ConflictException("Ya existe un usuario con ese email: " + newEmail));
    }
 
    public Mono<User> getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .switchIfEmpty(Mono.error(new NotFoundException("Usuario no encontrado: " + email)));
    }
 
    public Mono<UserInfo> getUserInfo(String email) {
        Mono<User> userMono = userRepository.findByEmail(email)
                .switchIfEmpty(Mono.error(new NotFoundException("Usuario no encontrado: " + email)));
        Mono<Boolean> verifiedMono = userRepository.isEmailVerified(email).defaultIfEmpty(false);
        return Mono.zip(userMono, verifiedMono)
                .map(t -> new UserInfo(t.getT1(), t.getT2()));
    }
 
    public Flux<User> getAllUsers() {
        return userRepository.findAllUser()
                .switchIfEmpty(Flux.error(new Exception("No se encontraron usuarios")));
    }
 
    public Mono<Boolean> setPassword(String email, String newPassword) {
        String hash = passwordHasher.hash(newPassword);
        return userRepository.updatePasswordHash(email, hash)
                .thenReturn(true);
    }
 
}
