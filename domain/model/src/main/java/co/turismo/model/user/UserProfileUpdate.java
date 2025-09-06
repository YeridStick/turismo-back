package co.turismo.model.user;


import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserProfileUpdate {
    private String fullName;
    private String urlAvatar;
    private String identificationType;
    private String identificationNumber;
}