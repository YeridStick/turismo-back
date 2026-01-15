package co.turismo.model.agency;

import co.turismo.model.tourpackage.TourPackage;
import co.turismo.model.tourpackage.TopPackage;
import co.turismo.model.tourpackage.TourPackageSalesSummary;
import co.turismo.model.visits.TopPlace;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class AgencyDashboard {
    private Agency agency;
    private List<TourPackage> packages;
    private List<TopPackage> topPackages;
    private List<TopPlace> topPlaces;
    private TourPackageSalesSummary salesSummary;
}
