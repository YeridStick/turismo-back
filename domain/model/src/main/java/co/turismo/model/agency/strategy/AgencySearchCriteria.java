package co.turismo.model.agency.strategy;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AgencySearchCriteria {
    private AgencySearchMode mode;
    private String q;
    private int limit;
    private int offset;
}
