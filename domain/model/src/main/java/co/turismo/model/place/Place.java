package co.turismo.model.place;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class Place {
    private Long id;
    private String name;
    private String description;
    private String category;
    private Double lat;     // derivado de geom
    private Double lng;     // derivado de geom
    private String address;
    private String phone;
    private String website;
    private Boolean verified;
    private Boolean active;
    private OffsetDateTime createdAt;
}
