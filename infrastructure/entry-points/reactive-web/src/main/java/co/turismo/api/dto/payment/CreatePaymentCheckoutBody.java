package co.turismo.api.dto.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(name = "CreatePaymentCheckoutRequest")
public record CreatePaymentCheckoutBody(
        @Schema(example = "wompi")
        String provider
) {}
