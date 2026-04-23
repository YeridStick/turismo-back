package co.turismo.r2dbc.tourpackage.repository;

import co.turismo.r2dbc.tourpackage.dto.TopPackageRowDto;
import co.turismo.r2dbc.tourpackage.dto.TourPackageSalesSummaryRow;
import co.turismo.r2dbc.tourpackage.entity.TourPackageData;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.query.ReactiveQueryByExampleExecutor;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

public interface TourPackageAdapterRepository extends ReactiveCrudRepository<TourPackageData, Long>,
        ReactiveQueryByExampleExecutor<TourPackageData> {

    @Query("""
                WITH inserted AS (
                    INSERT INTO tour_packages (
                        agency_id,
                        title,
                        city,
                        description,
                        days,
                        nights,
                        people,
                        rating,
                        reviews,
                        price,
                        original_price,
                        discount,
                        tag,
                        includes,
                        image,
                        is_active,
                        created_at
                    )
                    VALUES (
                        :agencyId,
                        :title,
                        :city,
                        :description,
                        :days,
                        :nights,
                        :people,
                        :rating,
                        :reviews,
                        :price,
                        :originalPrice,
                        :discount,
                        :tag,
                        COALESCE(:includes::text[], '{}'::text[]),
                        :image,
                        TRUE,
                        NOW()
                    )
                    RETURNING *
                ),
                place_links AS (
                    INSERT INTO tour_package_places (package_id, place_id)
                    SELECT inserted.id, unnest(COALESCE(:placeIds::bigint[], '{}'::bigint[]))
                    FROM inserted
                )
                SELECT
                    i.id,
                    i.agency_id,
                    i.title,
                    i.city,
                    i.description,
                    i.days,
                    i.nights,
                    i.people,
                    i.rating,
                    i.reviews,
                    i.price,
                    i.original_price,
                    i.discount,
                    i.tag,
                    i.includes,
                    i.image,
                    i.is_active,
                    i.created_at,
                    a.name AS agency_name,
                    COALESCE((
                        SELECT array_agg(pp.place_id)
                        FROM tour_package_places pp
                        WHERE pp.package_id = i.id
                    ), '{}'::bigint[]) AS place_ids
                FROM inserted i
                JOIN agencies a ON a.id = i.agency_id
            """)
    Mono<TourPackageData> insertPackage(
            @Param("agencyId") Long agencyId,
            @Param("title") String title,
            @Param("city") String city,
            @Param("description") String description,
            @Param("days") Integer days,
            @Param("nights") Integer nights,
            @Param("people") String people,
            @Param("rating") Double rating,
            @Param("reviews") Long reviews,
            @Param("price") Long price,
            @Param("originalPrice") Long originalPrice,
            @Param("discount") String discount,
            @Param("tag") String tag,
            @Param("includes") String[] includes,
            @Param("image") String image,
            @Param("placeIds") Long[] placeIds);

    @Query("""
                SELECT
                    p.id,
                    p.agency_id,
                    p.title,
                    p.city,
                    p.description,
                    p.days,
                    p.nights,
                    p.people,
                    p.rating,
                    p.reviews,
                    p.price,
                    p.original_price,
                    p.discount,
                    p.tag,
                    p.includes,
                    p.image,
                    p.is_active,
                    p.created_at,
                    a.name AS agency_name,
                    COALESCE((
                        SELECT array_agg(pp.place_id)
                        FROM tour_package_places pp
                        WHERE pp.package_id = p.id
                    ), '{}'::bigint[]) AS place_ids
                FROM tour_packages p
                JOIN agencies a ON a.id = p.agency_id
                WHERE p.is_active = TRUE
                ORDER BY p.created_at DESC
                LIMIT :limit
                OFFSET :offset
            """)
    Flux<TourPackageData> findAllProjected(@Param("limit") int limit, @Param("offset") int offset);

    @Query("""
                SELECT
                    p.id,
                    p.agency_id,
                    p.title,
                    p.city,
                    p.description,
                    p.days,
                    p.nights,
                    p.people,
                    p.rating,
                    p.reviews,
                    p.price,
                    p.original_price,
                    p.discount,
                    p.tag,
                    p.includes,
                    p.image,
                    p.is_active,
                    p.created_at,
                    a.name AS agency_name,
                    COALESCE((
                        SELECT array_agg(pp.place_id)
                        FROM tour_package_places pp
                        WHERE pp.package_id = p.id
                    ), '{}'::bigint[]) AS place_ids
                FROM tour_packages p
                JOIN agencies a ON a.id = p.agency_id
                WHERE p.id = :id
            """)
    Mono<TourPackageData> findByIdProjected(@Param("id") Long id);

    @Query("""
                SELECT
                    p.id,
                    p.agency_id,
                    p.title,
                    p.city,
                    p.description,
                    p.days,
                    p.nights,
                    p.people,
                    p.rating,
                    p.reviews,
                    p.price,
                    p.original_price,
                    p.discount,
                    p.tag,
                    p.includes,
                    p.image,
                    p.is_active,
                    p.created_at,
                    a.name AS agency_name,
                    COALESCE((
                        SELECT array_agg(pp.place_id)
                        FROM tour_package_places pp
                        WHERE pp.package_id = p.id
                    ), '{}'::bigint[]) AS place_ids
                FROM tour_packages p
                JOIN agencies a ON a.id = p.agency_id
                WHERE p.agency_id = :agencyId
                ORDER BY p.created_at DESC
                LIMIT :limit OFFSET :offset
            """)
    Flux<TourPackageData> findByAgencyProjected(@Param("agencyId") Long agencyId,
                                                @Param("limit") int limit,
                                                @Param("offset") int offset);

    @Query("""
                SELECT
                    p.id AS package_id,
                    p.title AS title,
                    COALESCE(SUM(s.quantity), 0)::int AS sold,
                    COALESCE(SUM(s.total_amount), 0)::bigint AS revenue
                FROM tour_package_sales s
                JOIN tour_packages p ON p.id = s.package_id
                WHERE p.agency_id = :agencyId
                  AND s.sold_at::date BETWEEN :from AND :to
                GROUP BY p.id, p.title
                ORDER BY sold DESC
                LIMIT :limit
            """)
    Flux<TopPackageRowDto> topSoldByAgency(@Param("agencyId") Long agencyId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("limit") int limit);

    @Query("""
                SELECT
                    COALESCE(SUM(s.quantity), 0)::bigint AS total_sold,
                    COALESCE(SUM(s.total_amount), 0)::bigint AS total_revenue
                FROM tour_package_sales s
                WHERE s.agency_id = :agencyId
                  AND s.sold_at::date BETWEEN :from AND :to
            """)
    Mono<TourPackageSalesSummaryRow> salesSummaryByAgency(@Param("agencyId") Long agencyId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("""
        UPDATE tour_packages
        SET title = COALESCE(:title, title),
            city = COALESCE(:city, city),
            description = COALESCE(:description, description),
            days = COALESCE(:days, days),
            nights = COALESCE(:nights, nights),
            people = COALESCE(:people, people),
            rating = COALESCE(:rating, rating),
            reviews = COALESCE(:reviews, reviews),
            price = COALESCE(:price, price),
            original_price = COALESCE(:originalPrice, original_price),
            discount = COALESCE(:discount, discount),
            tag = COALESCE(:tag, tag),
            includes = COALESCE(:includes::text[], includes),
            image = COALESCE(:image, image)
        WHERE id = :id
        RETURNING *
    """)
    Mono<TourPackageData> updatePackage(
            @Param("id") Long id,
            @Param("title") String title,
            @Param("city") String city,
            @Param("description") String description,
            @Param("days") Integer days,
            @Param("nights") Integer nights,
            @Param("people") String people,
            @Param("rating") Double rating,
            @Param("reviews") Long reviews,
            @Param("price") Long price,
            @Param("originalPrice") Long originalPrice,
            @Param("discount") String discount,
            @Param("tag") String tag,
            @Param("includes") String[] includes,
            @Param("image") String image
    );

    @Query("DELETE FROM tour_package_places WHERE package_id = :packageId")
    Mono<Void> deletePackagePlaces(@Param("packageId") Long packageId);

    @Query("INSERT INTO tour_package_places (package_id, place_id) VALUES (:packageId, :placeId)")
    Mono<Void> addPlaceToPackage(@Param("packageId") Long packageId, @Param("placeId") Long placeId);

    @Query("DELETE FROM tour_packages WHERE id = :id")
    Mono<Void> deletePackage(@Param("id") Long id);
}
