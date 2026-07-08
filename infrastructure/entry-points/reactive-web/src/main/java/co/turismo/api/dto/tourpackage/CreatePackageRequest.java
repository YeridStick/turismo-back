package co.turismo.api.dto.tourpackage;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(name = "TourPackageCreateRequest")
public record CreatePackageRequest(
        @NotBlank(message = "title es obligatorio")
        @Schema(example = "Aventura Completa Huila")
        String title,

        @Schema(example = "Tatacoa / San Agustín")
        String city,

        @NotBlank(message = "description es obligatorio")
        String description,

        @Positive(message = "days debe ser mayor a 0")
        Integer days,

        @PositiveOrZero(message = "nights debe ser mayor o igual a 0")
        Integer nights,

        String people,

        @DecimalMin(value = "0.0", message = "rating debe ser >= 0")
        @DecimalMax(value = "5.0", message = "rating debe ser <= 5")
        Double rating,

        @PositiveOrZero(message = "reviews debe ser >= 0")
        Long reviews,

        @NotNull(message = "price es obligatorio")
        @Positive(message = "price debe ser mayor a 0")
        Long price,

        @Positive(message = "originalPrice debe ser mayor a 0")
        Long originalPrice,

        String discount,
        String tag,
        List<String> includes,
        String image,
        @Positive(message = "agencyId debe ser mayor a 0")
        Long agencyId,
        String agencyEmail,

        @NotNull(message = "placeIds es obligatorio")
        @Size(min = 1, message = "Debe incluir al menos un lugar")
        List<Long> placeIds
) {
    @AssertTrue(message = "No puedes enviar agencyId y agencyEmail al mismo tiempo")
    public boolean isSingleAdminAgencyTarget() {
        return !hasText(agencyEmail) || agencyId == null;
    }

    public boolean hasAdminAgencyTarget() {
        return agencyId != null || hasText(agencyEmail);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
