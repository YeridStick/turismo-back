package co.turismo.api.handler;

import co.turismo.api.dto.response.ApiResponse;
import co.turismo.model.agency.Agency;
import co.turismo.model.agency.CreateAgencyRequest;
import co.turismo.usecase.agency.AgencyUseCase;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AgencyHandler {

    private final AgencyUseCase agencyUseCase;

    public Mono<ServerResponse> create(ServerRequest req) {
        return req.principal()
                .cast(Authentication.class)
                .map(Authentication::getName)
                .zipWith(req.bodyToMono(CreateAgencyBody.class))
                .flatMap(tuple -> {
                    String email = tuple.getT1();
                    CreateAgencyBody body = tuple.getT2();
                    var cmd = CreateAgencyRequest.builder()
                            .name(body.name())
                            .description(body.description())
                            .phone(body.phone())
                            .email(body.email())
                            .website(body.website())
                            .logoUrl(body.logoUrl())
                            .build();
                    return agencyUseCase.create(email, cmd);
                })
                .flatMap(agency -> ServerResponse.status(201)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.created(agency)));
    }

    public Mono<ServerResponse> addUser(ServerRequest req) {
        return req.principal()
                .cast(Authentication.class)
                .map(Authentication::getName)
                .zipWith(req.bodyToMono(AddAgencyUserBody.class))
                .flatMap(tuple -> agencyUseCase.addUserToMyAgency(tuple.getT1(), tuple.getT2().email()))
                .flatMap(agency -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.ok(agency)));
    }

    public Mono<ServerResponse> list(ServerRequest req) {
        return agencyUseCase.findAll()
                .collectList()
                .flatMap(list -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.ok(list)));
    }

    @Schema(name = "CreateAgencyRequest", description = "Cuerpo para crear una agencia")
    public record CreateAgencyBody(
            @Schema(description = "Nombre de la agencia", example = "Turismo Huila")
            @NotBlank String name,
            @Schema(description = "Descripción corta")
            String description,
            @Schema(description = "Teléfono de contacto")
            String phone,
            @Schema(description = "Email de la agencia")
            String email,
            @Schema(description = "Sitio web")
            String website,
            @Schema(description = "Logo URL")
            String logoUrl
    ) {
    }

    @Schema(name = "AddAgencyUserRequest", description = "Asociar usuario a la agencia del solicitante")
    public record AddAgencyUserBody(
            @Schema(description = "Email del usuario a asociar", example = "nuevo@correo.com")
            @NotBlank String email
    ) {
    }
}
