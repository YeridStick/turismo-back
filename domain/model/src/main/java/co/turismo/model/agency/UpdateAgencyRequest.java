package co.turismo.model.agency;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(toBuilder = true)
public class UpdateAgencyRequest {
    private String name;
    private String description;
    private String phone;
    private String email;
    private String website;
    private String logoUrl;
}
