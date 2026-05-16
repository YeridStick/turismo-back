package co.turismo.model.visits;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;

@Value
@Builder
public class UserFavoritePlace {
    Long placeId;
    String name;
    String address;
    String description;
    Integer categoryId;
    Double lat;
    Double lng;
    List<String> imageUrls;
    Instant favoritedAt;
}
