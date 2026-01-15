package co.turismo.r2dbc.category.repository;

import co.turismo.r2dbc.category.entity.CategoryData;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.query.ReactiveQueryByExampleExecutor;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CategoryAdapterRepository extends ReactiveCrudRepository<CategoryData, Long>,
        ReactiveQueryByExampleExecutor<CategoryData> {

    @Query("""
        SELECT id, slug, name, created_at
        FROM categories
        ORDER BY name ASC
    """)
    Flux<CategoryData> findAllProjected();

    @Query("""
        SELECT id, slug, name, created_at
        FROM categories
        WHERE id = :id
        LIMIT 1
    """)
    Mono<CategoryData> findByIdProjected(@Param("id") Long id);

    @Query("""
        INSERT INTO categories (
            slug,
            name,
            created_at
        )
        VALUES (
            :slug,
            :name,
            NOW()
        )
        RETURNING id, slug, name, created_at
    """)
    Mono<CategoryData> insertCategory(
            @Param("slug") String slug,
            @Param("name") String name
    );

    @Query("""
        UPDATE categories
        SET slug = COALESCE(:slug, slug),
            name = COALESCE(:name, name)
        WHERE id = :id
        RETURNING id, slug, name, created_at
    """)
    Mono<CategoryData> updateCategory(
            @Param("id") Long id,
            @Param("slug") String slug,
            @Param("name") String name
    );
}
