package co.turismo.r2dbc.visitsRepository.repository;

import co.turismo.r2dbc.visitsRepository.entity.PlaceVisitData;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.query.ReactiveQueryByExampleExecutor;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface VisitRepository extends ReactiveCrudRepository<PlaceVisitData, Long>,
        ReactiveQueryByExampleExecutor<PlaceVisitData> {

    @Query("""
        WITH user_point AS (
          SELECT ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography AS g
        )
        SELECT CASE
                 WHEN ST_DWithin(p.geom::geography, u.g, :radius)
                 THEN CAST(ST_Distance(p.geom::geography, u.g) AS INTEGER)
                 ELSE NULL
               END AS distance_m
        FROM places p, user_point u
        WHERE p.id = :placeId
      """)
    Mono<Integer> computeDistanceIfWithin(@Param("placeId") Long placeId,
                                          @Param("lat") double lat,
                                          @Param("lng") double lng,
                                          @Param("radius") int radius);

    @Query("""
        INSERT INTO place_visits(place_id, user_id, device_id, started_at, status, distance_m, accuracy_m, meta)
        VALUES (:placeId, :userId, :deviceId, now(), 'pending', :distanceM, :accuracyM, COALESCE(:meta::jsonb,'{}'::jsonb))
        RETURNING *
      """)
    Mono<PlaceVisitData> insertPending(@Param("placeId") Long placeId,
                                       @Param("userId") Long userId,
                                       @Param("deviceId") String deviceId,
                                       @Param("distanceM") Integer distanceM,
                                       @Param("accuracyM") Integer accuracyM,
                                       @Param("meta") String metaJson);

    @Query("""
        UPDATE place_visits
           SET status = 'confirmed', confirmed_at = now()
         WHERE id = :visitId AND status = 'pending'
        RETURNING *
      """)
    Mono<PlaceVisitData> confirmVisit(@Param("visitId") Long visitId);

    @Query("""
        SELECT 1
          FROM place_visits
         WHERE place_id = :placeId
           AND device_id = :deviceId
           AND date(started_at) = CURRENT_DATE
           AND status = 'confirmed'
         LIMIT 1
      """)
    Mono<Integer> existsConfirmedToday(@Param("placeId") Long placeId, @Param("deviceId") String deviceId);

    @Query("""
        INSERT INTO place_visit_daily(day, place_id, visits)
        VALUES (CURRENT_DATE, :placeId, 1)
        ON CONFLICT (day, place_id) DO UPDATE
          SET visits = place_visit_daily.visits + 1
      """)
    Mono<Void> upsertDaily(@Param("placeId") Long placeId);

    @Query("""
        SELECT p.id AS place_id, p.name, SUM(d.visits) AS visits
          FROM place_visit_daily d
          JOIN places p ON p.id = d.place_id
         WHERE d.day BETWEEN :from AND :to
         GROUP BY p.id, p.name
         ORDER BY visits DESC
         LIMIT :limit
      """)
    Flux<TopPlaceRow> topPlaces(@Param("from") String fromDate,
                                @Param("to") String toDate,
                                @Param("limit") int limit);

    interface TopPlaceRow {
        Long getPlace_id();
        String getName();
        Integer getVisits();
    }
}
