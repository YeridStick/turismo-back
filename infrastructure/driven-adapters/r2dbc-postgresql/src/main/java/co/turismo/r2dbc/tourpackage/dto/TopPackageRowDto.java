package co.turismo.r2dbc.tourpackage.dto;

import org.springframework.data.relational.core.mapping.Column;

public class TopPackageRowDto {
    @Column("package_id")
    private Long packageId;

    @Column("title")
    private String title;

    @Column("sold")
    private Integer sold;

    @Column("revenue")
    private Long revenue;

    public Long getPackageId() { return packageId; }
    public String getTitle() { return title; }
    public Integer getSold() { return sold; }
    public Long getRevenue() { return revenue; }
}
