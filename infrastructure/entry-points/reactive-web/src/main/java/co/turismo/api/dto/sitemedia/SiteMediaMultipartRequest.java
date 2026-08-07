package co.turismo.api.dto.sitemedia;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Esquema documental del formulario multipart. La carga real es procesada por
 * SiteMediaHandler como FormFieldPart + FilePart.
 */
@Schema(name = "SiteMediaMultipartRequest", description = "Formulario multipart para cargar un archivo multimedia")
public record SiteMediaMultipartRequest(
        @Schema(description = "Categoría multimedia", allowableValues = {"images", "videos", "models-3d"}, example = "images", requiredMode = Schema.RequiredMode.REQUIRED)
        String category,

        @Schema(description = "Archivo binario que se almacenará en S3", type = "string", format = "binary", requiredMode = Schema.RequiredMode.REQUIRED)
        byte[] file
) {
}
