package co.turismo.model.agency;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class CreateAgencyRequest {
    private String name;
    private String description;
    private String phone;
    private String email;
    private String website;
    private String logoUrl;
}
