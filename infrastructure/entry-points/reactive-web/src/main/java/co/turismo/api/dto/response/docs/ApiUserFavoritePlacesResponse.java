package co.turismo.api.dto.response.docs;

import co.turismo.api.dto.visit.UserFavoritePlaceDTO;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "ApiUserFavoritePlacesResponse", description = "Listado de lugares favoritos del usuario autenticado")
public record ApiUserFavoritePlacesResponse(
        @Schema(example = "200")
        int status,
        @Schema(example = "OK")
        String message,
        @ArraySchema(
                schema = @Schema(implementation = UserFavoritePlaceDTO.class),
                arraySchema = @Schema(description = "Favoritos ordenados del más reciente al más antiguo")
        )
        List<UserFavoritePlaceDTO> data
) {
}
