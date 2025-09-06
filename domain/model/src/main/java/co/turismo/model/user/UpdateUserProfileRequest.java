package co.turismo.model.user;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class UpdateUserProfileRequest {
    String fullName;
    String urlAvatar;
    String identificationType;
    String identificationNumber;
}