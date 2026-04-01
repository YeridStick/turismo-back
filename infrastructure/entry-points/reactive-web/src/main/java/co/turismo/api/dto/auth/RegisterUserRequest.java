package co.turismo.api.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Schema(name = "RegisterUserRequest", description = "Datos requeridos para registrar un nuevo usuario")
public record RegisterUserRequest(

        @Schema(description = "Nombre completo del usuario", example = "Ana Pérez García")
        @NotBlank(message = "El nombre es requerido")
        @Size(min = 3, max = 100, message = "El nombre debe tener entre 3 y 100 caracteres")
        String full_name,

        @Schema(description = "Correo electrónico del usuario", example = "ana@example.com")
        @NotBlank(message = "El email es requerido")
        @Email(message = "Email con formato inválido")
        String email,

        @Schema(description = "URL de la imagen de perfil", example = "https://cdn.example.com/avatar.png", nullable = true)
        @Size(max = 500, message = "La URL del avatar no puede superar los 500 caracteres")
        String url_avatar,

        @Schema(description = "Tipo de documento de identificación", example = "CC", allowableValues = {"CC", "CE", "PASSPORT", "NIT"})
        @NotBlank(message = "El tipo de identificación es requerido")
        @Pattern(regexp = "^(CC|CE|PASSPORT|NIT)$", message = "Tipo de identificación inválido")
        String identification_type,

        @Schema(description = "Número de documento de identificación", example = "1234567890")
        @NotBlank(message = "El número de identificación es requerido")
        @Pattern(regexp = "^[a-zA-Z0-9\\-]{4,20}$", message = "El número de identificación debe tener entre 4 y 20 caracteres alfanuméricos")
        String identification_number,

        @Schema(description = "Contraseña del usuario", example = "MiPassword@2024")
        @NotBlank(message = "La contraseña es requerida")
        @Size(min = 8, max = 64, message = "La contraseña debe tener entre 8 y 64 caracteres")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$",
                message = "La contraseña debe tener al menos una mayúscula, una minúscula, un número y un carácter especial"
        )
        String password

) {}