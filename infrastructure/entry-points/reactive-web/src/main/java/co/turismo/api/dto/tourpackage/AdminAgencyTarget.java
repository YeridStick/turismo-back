package co.turismo.api.dto.tourpackage;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Positive;

public record AdminAgencyTarget(
        @Positive(message = "agencyId debe ser mayor a 0")
        Long agencyId,

        @Email(message = "agencyEmail debe tener un formato válido")
        String agencyEmail
) {
    @AssertTrue(message = "Debes enviar agencyId o agencyEmail")
    public boolean isAnyTargetPresent() {
        return agencyId != null || hasText(agencyEmail);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
