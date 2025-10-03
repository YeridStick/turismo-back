package co.turismo.r2dbc.visitsRepository.repository;

import co.turismo.r2dbc.visitsRepository.dto.PlaceBriefRow;
import co.turismo.r2dbc.visitsRepository.dto.PlaceNearbyRow;
import co.turismo.r2dbc.visitsRepository.dto.TopPlaceRowDto;
import co.turismo.r2dbc.visitsRepository.entity.PlaceVisitData;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.query.ReactiveQueryByExampleExecutor;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

public interface VisitRepository extends ReactiveCrudRepository<PlaceVisitData, Long>,
        ReactiveQueryByExampleExecutor<PlaceVisitData> {

    @Query("""
        WITH user_point AS (
          SELECT ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography AS g
        )
        SELECT CAST(ST_Distance(p.geom::geography, u.g) AS INTEGER) AS distance_m
        FROM places p, user_point u
        WHERE p.id = :placeId
          AND ST_DWithin(p.geom::geography, u.g, :radius)
      """)
    Mono<Integer> computeDistanceIfWithin(@Param("placeId") Long placeId,
                                          @Param("lat") double lat,
                                          @Param("lng") double lng,
                                          @Param("radius") int radius);

    @Query("""
        INSERT INTO place_visits(
           place_id, user_id, device_id,
           started_at, status,
           distance_m, accuracy_m, meta
         )
         VALUES (
           :placeId, :userId, :deviceId,
           now(), 'pending',
           :distanceM, :accuracyM, COALESCE(:meta::jsonb,'{}'::jsonb)
         )
         RETURNING *
    """)
    Mono<PlaceVisitData> insertPending(@Param("placeId") Long placeId,
                                       @Param("userId") Long userId,
                                       @Param("deviceId") String deviceId,
                                       @Param("distanceM") Integer distanceM,
                                       @Param("accuracyM") Integer accuracyM,
                                       @Param("meta") String metaJson);

    // VisitRepository.java
    @Query("""
        UPDATE place_visits
         SET status = 'confirmed',
             confirmed_at = now(),
             lat = :lat,
             lng = :lng,
             accuracy_m = :accuracyM,
             meta = COALESCE(:meta::jsonb, meta)
       WHERE id = :visitId
         AND status = 'pending'
     RETURNING *
    """)
    Mono<PlaceVisitData> confirmVisit(@Param("visitId") Long visitId,
                                      @Param("lat") double lat,
                                      @Param("lng") double lng,
                                      @Param("accuracyM") Integer accuracyM,
                                      @Param("meta") String metaJson);


    @Query("""
      SELECT 1
        FROM place_visits
       WHERE place_id = :placeId
         AND status   = 'confirmed'
         AND confirmed_on_utc = (now() AT TIME ZONE 'UTC')::date
         AND (
               (:userId IS NOT NULL AND user_id = :userId)
            OR (:userId IS NULL    AND device_id = :deviceId)
         )
       LIMIT 1
    """)
    Mono<Integer> existsConfirmedToday(@Param("placeId") Long placeId,
                                       @Param("userId") Long userId,
                                       @Param("deviceId") String deviceId);

    @Query("""
        INSERT INTO place_visit_daily(day, place_id, visits)
        VALUES (CURRENT_DATE, :placeId, 1)
        ON CONFLICT (day, place_id) DO UPDATE
          SET visits = place_visit_daily.visits + 1
    """)
    Mono<Void> upsertDaily(@Param("placeId") Long placeId);

    @Query("""
      SELECT p.id AS placeId,
             p.name AS name,
             SUM(d.visits)::int AS visits
        FROM place_visit_daily d
        JOIN places p ON p.id = d.place_id
       WHERE d.day BETWEEN :from AND :to
       GROUP BY p.id, p.name
       ORDER BY visits DESC
       LIMIT :limit
    """)
    Flux<TopPlaceRowDto> topPlaces(@Param("from") LocalDate from,
                                   @Param("to")   LocalDate to,
                                   @Param("limit") int limit);

    @Query("""
      SELECT id,
             name,
             address,
             description,
             category_id,
             ST_Y(geom::geometry) AS lat,
             ST_X(geom::geometry) AS lng,
             image_urls
        FROM places
       WHERE id = :placeId
    """)
    Mono<PlaceBriefRow> getPlaceBrief(@Param("placeId") Long placeId);

    @Query("""
      WITH u AS (
        SELECT ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography AS g
      )
      SELECT
          p.id,
          p.name,
          p.address,
          p.description,
          p.category_id,
          ST_Y(p.geom::geometry) AS lat,
          ST_X(p.geom::geometry) AS lng,
          CAST(ST_Distance(p.geom::geography, u.g) AS INTEGER) AS distance_m
      FROM places p, u
      WHERE p.geom IS NOT NULL
        AND ST_DWithin(p.geom::geography, u.g, :radius)
      ORDER BY COALESCE(p.is_verified, FALSE) DESC, distance_m ASC
      LIMIT :limit
    """)
    Flux<PlaceNearbyRow> findNearby(@Param("lat") double lat,
                                    @Param("lng") double lng,
                                    @Param("radius") int radius,
                                    @Param("limit") int limit);


}
