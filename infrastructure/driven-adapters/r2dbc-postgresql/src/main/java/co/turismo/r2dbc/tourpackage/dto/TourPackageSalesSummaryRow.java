package co.turismo.r2dbc.tourpackage.dto;

import org.springframework.data.relational.core.mapping.Column;

public class TourPackageSalesSummaryRow {
    @Column("total_sold")
    private Long totalSold;

    @Column("total_revenue")
    private Long totalRevenue;

    public Long getTotalSold() { return totalSold; }
    public Long getTotalRevenue() { return totalRevenue; }
}
