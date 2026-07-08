package co.turismo.api.dto.tourpackage;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(name = "UpdatePackageBody")
public record UpdatePackageBody(
        @Size(min = 1, message = "title no puede ser vacío si se envía")
        String title,

        String city,

        @Size(min = 1, message = "description no puede ser vacía si se envía")
        String description,

        @Positive(message = "days debe ser mayor a 0")
        Integer days,

        @PositiveOrZero(message = "nights debe ser mayor o igual a 0")
        Integer nights,

        String people,

        @DecimalMin(value = "0.0")
        @DecimalMax(value = "5.0")
        Double rating,

        @PositiveOrZero
        Long reviews,

        @Positive(message = "price debe ser mayor a 0")
        Long price,

        @Positive
        Long originalPrice,

        String discount,
        String tag,
        List<String> includes,
        String image,

        @Size(min = 1, message = "placeIds no puede estar vacío si se envía")
        List<Long> placeIds
) {}
