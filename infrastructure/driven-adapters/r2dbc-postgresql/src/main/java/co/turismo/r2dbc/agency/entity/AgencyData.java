package co.turismo.r2dbc.agency.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Table("agencies")
public class AgencyData {

    @Id
    private Long id;

    private String name;
    private String description;
    private String phone;
    private String email;
    private String website;

    @Column("logo_url")
    private String logoUrl;

    @Column("created_at")
    private OffsetDateTime createdAt;
}
