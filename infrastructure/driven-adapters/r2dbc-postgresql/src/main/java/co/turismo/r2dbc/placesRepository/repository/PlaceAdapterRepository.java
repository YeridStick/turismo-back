package co.turismo.r2dbc.placesRepository.repository;

import co.turismo.r2dbc.placesRepository.entity.PlaceData;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PlaceAdapterRepository extends ReactiveCrudRepository<PlaceData, Long> {

    @Query("""
        INSERT INTO places(owner_user_id, name, description, category, geom, address, phone, website)
        VALUES (:ownerUserId, :name, :description, :category,
                ST_SetSRID(ST_MakePoint(:lng, :lat), 4326),
                :address, :phone, :website)
        RETURNING id, name, description, category,
                  ST_Y(geom) AS lat, ST_X(geom) AS lng,
                  address, phone, website,
                  is_verified AS isVerified, is_active AS isActive, created_at AS createdAt
    """)
    Mono<PlaceData> insertPlace(
            @Param("ownerUserId") Long ownerUserId,
            @Param("name") String name,
            @Param("description") String description,
            @Param("category") String category,
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("address") String address,
            @Param("phone") String phone,
            @Param("website") String website
    );

    @Query("""
        SELECT p.id, p.name, p.description, p.category,
               ST_Y(p.geom) AS lat, ST_X(p.geom) AS lng,
               p.address, p.phone, p.website,
               p.is_verified, p.is_active, p.created_at,
               ST_DistanceSphere(
                 p.geom,
                 ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)
               ) AS distanceMeters
          FROM places p
         WHERE p.is_active = TRUE
           AND ST_DWithin(
                 p.geom::geography,
                 ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
                 :radiusMeters
               )
      ORDER BY distanceMeters ASC
        LIMIT :limit
    """)
    Flux<PlaceData> findNearby(
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("radiusMeters") double radiusMeters,
            @Param("limit") int limit
    );


    @Query("""
        SELECT p.id, p.name, p.description, p.category,
               ST_Y(p.geom) AS lat, ST_X(p.geom) AS lng,
               p.address, p.phone, p.website,
               p.is_verified, p.is_active, p.created_at
          FROM places p
          JOIN place_owners po ON po.place_id = p.id
          JOIN users u         ON u.id = po.user_id
         WHERE lower(u.email) = lower(:email)
    """)
    Flux<PlaceData> findMine(@Param("email") String email);

    @Query("""
        UPDATE places
           SET is_verified = :verified,
               is_active   = :active,
               verified_by = :verifiedBy,
               verified_at = NOW()
         WHERE id = :id
     RETURNING id, owner_user_id AS ownerUserId, name, description, category,
               ST_Y(geom) AS lat, ST_X(geom) AS lng,
               address, phone, website,
               is_verified AS isVerified, is_active AS isActive, created_at AS createdAt
    """)
    Mono<PlaceData> verifyPlace(
            @Param("id") Long id,
            @Param("verified") boolean verified,
            @Param("active") boolean active,
            @Param("verifiedBy") Long verifiedBy
    );

    @Query(
        """
        UPDATE places
               SET is_active = :active
             WHERE id = :id
         RETURNING id, name, description, category,
                   ST_Y(geom) AS lat, ST_X(geom) AS lng,
                   address, phone, website,
                   is_verified, is_active, created_at
        """)
    Mono<PlaceData> setActive(@Param("id") Long id, @Param("active") boolean active);


    @Query("""
        SELECT p.id, p.name, p.description, p.category,
               ST_Y(p.geom) AS lat, ST_X(p.geom) AS lng,
               p.address, p.phone, p.website,
               p.is_verified AS isVerified, p.is_active AS isActive, p.created_at AS createdAt
          FROM places p
          JOIN place_owners po ON po.place_id = p.id
         WHERE po.user_id = :ownerId
         ORDER BY p.created_at DESC
    """)
    Flux<PlaceData> findByOwnerId(@Param("ownerId") Long ownerId);

    @Query("""
        SELECT p.id, p.name, p.description, p.category,
               ST_Y(p.geom) AS lat, ST_X(p.geom) AS lng,
               p.address, p.phone, p.website,
               p.is_verified AS isVerified, p.is_active AS isActive, p.created_at AS createdAt
          FROM places p
         WHERE p.id = :id
    """)
    Mono<PlaceData> findOneProjected(@Param("id") Long id);
}
