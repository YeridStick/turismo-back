package co.turismo.model.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class RegisterUserCommand {
    private final String fullName;
    private final String email;
    private final String urlAvatar;
    private final String identificationType;
    private final String identificationNumber;
    private final String password;

    public User toUser() {
        return User.builder()
                .fullName(this.fullName)
                .email(this.email.trim().toLowerCase())
                .urlAvatar(this.urlAvatar)
                .identificationType(this.identificationType)
                .identificationNumber(this.identificationNumber)
                .build();
    }
}