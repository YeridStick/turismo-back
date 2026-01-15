package co.turismo.r2dbc.category.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("categories")
public class CategoryData {

    @Id
    private Long id;

    private String slug;
    private String name;

    @Column("created_at")
    private OffsetDateTime createdAt;
}
