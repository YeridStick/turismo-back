package co.turismo.r2dbc.visitsRepository.dto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.relational.core.mapping.Column;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
public class UserFavoritePlaceRow {
    @Column("id")          private Long id;
    @Column("name")        private String name;
    @Column("address")     private String address;
    @Column("description") private String description;
    @Column("category_id") private Integer categoryId;
    @Column("lat")         private Double lat;
    @Column("lng")         private Double lng;
    @Column("image_urls")  private List<String> imageUrls;
    @Column("favorited_at") private Instant favoritedAt;
}
