package co.turismo.r2dbc.placesRepository.repository;

import co.turismo.r2dbc.placesRepository.entity.PlaceData;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.query.ReactiveQueryByExampleExecutor;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PlaceAdapterRepository extends ReactiveCrudRepository<PlaceData, Long> ,
        ReactiveQueryByExampleExecutor<PlaceData> {

    // CREATE
    @Query("""
        INSERT INTO places(
            owner_user_id, name, description, category_id, geom, address, phone, website, image_urls,
            is_verified, is_active, created_at
        )
        VALUES (
            :ownerId, :name, :description, :categoryId,
            ST_SetSRID(ST_MakePoint(:lng, :lat), 4326),
            :address, :phone, :website,
            COALESCE(:imageUrls::text[], '{}'::text[]),
            FALSE, TRUE, NOW()
        )
        RETURNING
            id,
            owner_user_id,
            name, description,
            category_id,
            ST_Y(geom) AS lat,
            ST_X(geom) AS lng,
            address, phone, website,
            image_urls,
            is_verified,
            is_active,
            created_at
    """)
    Mono<PlaceData> insertPlace(
            @Param("ownerId") Long ownerId,
            @Param("name") String name,
            @Param("description") String description,
            @Param("categoryId") Long categoryId,
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("address") String address,
            @Param("phone") String phone,
            @Param("website") String website,
            @Param("imageUrls") String[] imageUrls
    );

    // NEARBY
    @Query("""
        SELECT
            p.id,
            p.owner_user_id,
            p.name, p.description,
            p.category_id,
            ST_Y(p.geom) AS lat,
            ST_X(p.geom) AS lng,
            p.address, p.phone, p.website,
            p.image_urls,
            p.is_verified,
            p.is_active,
            p.created_at,
            ST_DistanceSphere(p.geom, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)) AS distance_meters
        FROM places p
        WHERE p.is_active = TRUE
          AND ST_DWithin(
                p.geom::geography,
                ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
                :radiusMeters
              )
          AND (:categoryId IS NULL OR p.category_id = :categoryId)
        ORDER BY distance_meters ASC
        LIMIT :limit
    """)
    Flux<PlaceData> findNearby(
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("radiusMeters") double radiusMeters,
            @Param("limit") int limit,
            @Param("categoryId") Long categoryId
    );


    @Query("""
        SELECT
            p.id,
            p.owner_user_id,
            p.name, p.description,
            p.category_id,
            ST_Y(p.geom) AS lat,
            ST_X(p.geom) AS lng,
            p.address, p.phone, p.website,
            p.image_urls,
            p.is_verified,
            p.is_active,
            p.created_at
        FROM places p
    """)
    Flux<PlaceData> findAll();

    @Query("""
        WITH inp AS (
          SELECT
            NULLIF(
              translate(lower(trim(:q)),
                'áéíóúäëïöüñÁÉÍÓÚÄËÏÖÜÑ', 'aeiouaeiounaeiouaeioun'
              ),
              ''
            ) AS qnorm
        )
        SELECT
          p.id,
          p.owner_user_id,
          p.name, p.description,
          p.category_id,
          ST_Y(p.geom) AS lat,
          ST_X(p.geom) AS lng,
          p.address, p.phone, p.website,
          p.image_urls,
          p.is_verified,
          p.is_active,
          p.created_at,
          CASE
            WHEN :lat IS NOT NULL AND :lng IS NOT NULL
            THEN ST_DistanceSphere(p.geom, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326))
            ELSE NULL
          END AS distance_meters
        FROM places p
        CROSS JOIN inp
        WHERE p.is_active = TRUE
          AND (:categoryId IS NULL OR p.category_id = :categoryId)
          AND (
            -- si q viene vacío -> no filtramos por texto
            (SELECT qnorm FROM inp) IS NULL
            OR
            -- buscamos en nombre + dirección + descripción
            translate(lower(coalesce(p.name,'') || ' ' || coalesce(p.address,'') || ' ' || coalesce(p.description,'')),
                      'áéíóúäëïöüñÁÉÍÓÚÄËÏÖÜÑ', 'aeiouaeiounaeiouaeioun')
            LIKE '%' || (SELECT qnorm FROM inp) || '%'
          )
          AND (
            :onlyNearby = FALSE
            OR (
              :lat IS NOT NULL AND :lng IS NOT NULL AND :radiusMeters IS NOT NULL
              AND ST_DWithin(
                p.geom::geography,
                ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
                :radiusMeters
              )
            )
          )
        ORDER BY
          -- si pediste solo cercanos con coords: primero por distancia
          CASE
            WHEN :onlyNearby = TRUE AND :lat IS NOT NULL AND :lng IS NOT NULL
            THEN ST_DistanceSphere(p.geom, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326))
          END ASC NULLS LAST,
          -- si no hay cercanía, aproxima relevancia: posición de la subcadena (más adelante = peor)
          NULLIF(
            position(
              (SELECT qnorm FROM inp) IN
              translate(lower(coalesce(p.name,'') || ' ' || coalesce(p.address,'') || ' ' || coalesce(p.description,'')),
                        'áéíóúäëïöüñÁÉÍÓÚÄËÏÖÜÑ', 'aeiouaeiounaeiouaeioun')
            ),
            0
          ) ASC NULLS LAST,
          p.created_at DESC
        LIMIT :limit
        OFFSET :offset
    """)
    Flux<PlaceData> search(
            @Param("q") String q,
            @Param("categoryId") Long categoryId,
            @Param("onlyNearby") boolean onlyNearby,
            @Param("lat") Double lat,
            @Param("lng") Double lng,
            @Param("radiusMeters") Double radiusMeters,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    // PATCH (actualización parcial)
    @Query("""
        UPDATE places SET
            name        = COALESCE(:name,        name),
            description = COALESCE(:description, description),
            category_id = COALESCE(:categoryId,  category_id),
            address     = COALESCE(:address,     address),
            phone       = COALESCE(:phone,       phone),
            website     = COALESCE(:website,     website),
            image_urls  = COALESCE(:imageUrls::text[], image_urls),
            geom        = CASE
                           WHEN :lat IS NOT NULL AND :lng IS NOT NULL
                           THEN ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)
                           ELSE geom
                          END
        WHERE id = :id
        RETURNING
            id,
            owner_user_id,
            name, description,
            category_id,
            ST_Y(geom) AS lat,
            ST_X(geom) AS lng,
            address, phone, website,
            image_urls,
            is_verified,
            is_active,
            created_at
    """)
    Mono<PlaceData> patchPlace(
            @Param("id") Long id,
            @Param("name") String name,
            @Param("description") String description,
            @Param("categoryId") Long categoryId,
            @Param("lat") Double lat,
            @Param("lng") Double lng,
            @Param("address") String address,
            @Param("phone") String phone,
            @Param("website") String website,
            @Param("imageUrls") String[] imageUrls
    );

    // (Compat) Mis lugares por email – ya sin co-owners
    @Query("""
        SELECT
            p.id,
            p.owner_user_id,
            p.name, p.description,
            p.category_id,
            ST_Y(p.geom) AS lat,
            ST_X(p.geom) AS lng,
            p.address, p.phone, p.website,
            p.image_urls,
            p.is_verified,
            p.is_active,
            p.created_at
        FROM places p
        JOIN users u ON u.id = p.owner_user_id
        WHERE lower(u.email) = lower(:email)
        ORDER BY p.created_at DESC
    """)
    Flux<PlaceData> findMine(@Param("email") String email);

    // VERIFY (admin)
    @Query("""
        UPDATE places
           SET is_verified = :verified,
               is_active   = :active,
               verified_by = :verifiedBy,
               verified_at = NOW()
         WHERE id = :id
        RETURNING
            id,
            owner_user_id,
            name, description,
            category_id,
            ST_Y(geom) AS lat,
            ST_X(geom) AS lng,
            address, phone, website,
            image_urls,
            is_verified,
            is_active,
            created_at
    """)
    Mono<PlaceData> verifyPlace(
            @Param("id") Long id,
            @Param("verified") boolean verified,
            @Param("active") boolean active,
            @Param("verifiedBy") Long verifiedBy
    );

    // SET ACTIVE (admin u otros flujos)
    @Query("""
        UPDATE places
           SET is_active = :active
         WHERE id = :id
        RETURNING
            id,
            owner_user_id,
            name, description,
            category_id,
            ST_Y(geom) AS lat,
            ST_X(geom) AS lng,
            address, phone, website,
            image_urls,
            is_verified,
            is_active,
            created_at
    """)
    Mono<PlaceData> setActive(
            @Param("id") Long id,
            @Param("active") boolean active
    );

    // ONE (proyección simple)
    @Query("""
        SELECT
            p.id,
            p.owner_user_id,
            p.name, p.description,
            p.category_id,
            ST_Y(p.geom) AS lat,
            ST_X(p.geom) AS lng,
            p.address, p.phone, p.website,
            p.image_urls,
            p.is_verified,
            p.is_active,
            p.created_at
        FROM places p
        WHERE p.id = :id
    """)
    Mono<PlaceData> findOneProjected(@Param("id") Long id);

    // BY OWNER ID (para /mine vía adapter)
    @Query("""
        SELECT
            p.id,
            p.owner_user_id,
            p.name, p.description,
            p.category_id,
            ST_Y(p.geom) AS lat,
            ST_X(p.geom) AS lng,
            p.address, p.phone, p.website,
            p.image_urls,
            p.is_verified,
            p.is_active,
            p.created_at
        FROM places p
        WHERE p.owner_user_id = :ownerId
        ORDER BY p.created_at DESC
    """)
    Flux<PlaceData> findByOwnerId(@Param("ownerId") Long ownerId);

    // SET ACTIVE si es dueño (en una sola sentencia)
    @Query("""
        UPDATE places
           SET is_active = :active
         WHERE id = :placeId
           AND owner_user_id = :ownerId
        RETURNING
            id,
            owner_user_id,
            name, description,
            category_id,
            ST_Y(geom) AS lat,
            ST_X(geom) AS lng,
            address, phone, website,
            image_urls,
            is_verified,
            is_active,
            created_at
    """)
    Mono<PlaceData> setActiveIfOwner(@Param("placeId") Long placeId,
                                     @Param("active")  Boolean active,
                                     @Param("ownerId") Long ownerId);
}
