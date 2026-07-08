package co.turismo.api.mapper;

import co.turismo.api.dto.auth.RegisterUserRequest;
import co.turismo.api.dto.response.PasswordUpdateResponse;
import co.turismo.api.dto.response.UserInfoResponse;
import co.turismo.model.user.RegisterUserCommand;
import co.turismo.model.user.UserInfo;

public final class UserMapper {

    private UserMapper() {}

    public static RegisterUserCommand toRegisterCommand(RegisterUserRequest request) {
        return new RegisterUserCommand(
                request.full_name(),
                request.email(),
                request.url_avatar(),
                request.identification_type(),
                request.identification_number(),
                request.password()
        );
    }

    public static UserInfoResponse toUserInfoResponse(UserInfo info) {
        return new UserInfoResponse(
                info.user(),
                info.emailVerified()
        );
    }

    public static PasswordUpdateResponse toPasswordUpdateResponse() {
        return new PasswordUpdateResponse("success", "Contrasena procesada correctamente");
    }
}
