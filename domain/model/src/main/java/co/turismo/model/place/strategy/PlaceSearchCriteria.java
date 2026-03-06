package co.turismo.model.place.strategy;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PlaceSearchCriteria {
    private PlaceSearchMode mode;
    private String q;              // nullable
    private Long categoryId;       // nullable
    private boolean onlyNearby;    // no nullable (default false)
    private Double lat;            // nullable
    private Double lng;            // nullable
    private Double radiusMeters;   // nullable
    private int page;              // no nullable
    private int size;
}