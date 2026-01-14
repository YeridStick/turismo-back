package co.turismo.r2dbc.agency.repository;

import co.turismo.r2dbc.agency.entity.AgencyData;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.query.ReactiveQueryByExampleExecutor;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface AgencyAdapterRepository extends ReactiveCrudRepository<AgencyData, Long>,
        ReactiveQueryByExampleExecutor<AgencyData> {

    @Query("""
        SELECT a.id,
               a.name,
               a.description,
               a.phone,
               a.email,
               a.website,
               a.logo_url,
               a.created_at
        FROM agencies a
        JOIN agency_users au ON au.agency_id = a.id
        JOIN users u ON u.id = au.user_id
        WHERE lower(u.email) = lower(:email)
        LIMIT 1
    """)
    Mono<AgencyData> findByUserEmail(@Param("email") String email);

    @Query("""
        INSERT INTO agencies (
            name,
            description,
            phone,
            email,
            website,
            logo_url,
            created_at
        )
        VALUES (
            :name,
            :description,
            :phone,
            :email,
            :website,
            :logoUrl,
            NOW()
        )
        RETURNING id, name, description, phone, email, website, logo_url, created_at
    """)
    Mono<AgencyData> insertAgency(
            @Param("name") String name,
            @Param("description") String description,
            @Param("phone") String phone,
            @Param("email") String email,
            @Param("website") String website,
            @Param("logoUrl") String logoUrl
    );

    @Query("""
        INSERT INTO agency_users (agency_id, user_id, created_at)
        VALUES (:agencyId, :userId, NOW())
        ON CONFLICT DO NOTHING
    """)
    Mono<Void> addUserToAgency(@Param("agencyId") Long agencyId, @Param("userId") Long userId);

    @Query("""
        SELECT id,
               name,
               description,
               phone,
               email,
               website,
               logo_url,
               created_at
        FROM agencies
        ORDER BY created_at DESC
    """)
    reactor.core.publisher.Flux<AgencyData> findAllProjected();
}
