package co.turismo.r2dbc.visitsRepository.dto;

import org.springframework.data.relational.core.mapping.Column;

public class TopPlaceRowDto {
    @Column("placeid")
    private Long placeId;

    @Column("name")
    private String name;

    @Column("visits")
    private Integer visits;

    public Long getPlaceId() { return placeId; }
    public String getName() { return name; }
    public Integer getVisits() { return visits; }
}