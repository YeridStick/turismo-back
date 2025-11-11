package co.turismo.api;

import co.turismo.api.config.ConstantsEntryPoint;
import co.turismo.api.handler.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.net.URI;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration
public class RouterRest {
    @Bean
    public RouterFunction<ServerResponse> routerFunction(
            UserHandler userHandler,
            AuthenticateHandler authenticateHandler,
            PlacesHandler placesHandler,
            VisitHandler visitHandler,
            GeocodeHandler geocodeHandler,
            ReviewsHandler reviewsHandler,
            FeedbackHandler feedbackHandler
    ) {
        return route()
                // Docs
                .GET("/docs", request -> ServerResponse.temporaryRedirect(URI.create("/docs/index.html")).build())
                // Auth
                .GET(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.AUTH_TOTP_STATUS_PATH, authenticateHandler::totpStatus)
                .POST(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.AUTH_TOTP_SETUP_PATH,   authenticateHandler::totpSetup)
                .POST(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.AUTH_TOTP_CONFIRM_PATH, authenticateHandler::totpConfirm)
                .POST(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.AUTH_LOGIN_TOTP_PATH,   authenticateHandler::loginTotp)
                .POST(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.AUTH_REFRESH_PATH, authenticateHandler::refresh)

                // Users
                .POST(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.CREATEUSER, userHandler::createUser)
                .GET(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.INFOUSER, userHandler::getInfoUser)
                .GET(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.ALLUSER, userHandler::getAllUsers)
                .PATCH(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.USERS_ME_PATH, userHandler::updateMyProfile)

                // Places (público autenticado / owners)
                .POST (ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.PLACES_BASE_PATH,     placesHandler::create)
                .GET  (ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.PLACES_NEARBY_PATH,   placesHandler::findNearby)
                .GET  (ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.PLACES_SEARCH_PATH,   placesHandler::search)
                .GET  (ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.PLACES_ALL_PATH,   placesHandler::findAllPlaces)
                .GET  (ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.PLACES_MINE_PATH,     placesHandler::myPlaces)
                .GET  (ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.PLACES_ID_PATH,     placesHandler::findByIdPlace)
                .PATCH(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.PLACES_ACTIVE_PATH,   placesHandler::setActive)
                .PATCH(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.PLACES_ID_PATH,       placesHandler::patch)
                .DELETE(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.PLACES_ID_PATH, placesHandler::delete)

                // Debug
                .GET(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.DEBUG, placesHandler::debug)

                // Places - Admin
                .PATCH(ConstantsEntryPoint.API_ADMIN + ConstantsEntryPoint.ADMIN_PLACES_VERIFY_PATH, placesHandler::verify)

                // Geocode - Generar corrdenadas
                .POST(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.GETCOORDINATES, geocodeHandler::geocode)
                .GET  (ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.PLACES_NEARBY_PATH_PLACE, visitHandler::nearby)

                // Visits
                .POST (ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.VISITS_CHECKIN_PATH, visitHandler::checkin)
                .PATCH(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.VISITS_CONFIRM_PATH, visitHandler::confirm)
                .GET  (ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.ANALYTICS_TOP_PLACES_PATH, visitHandler::topPlaces)

                // Reviews
                .GET ("/api/pruebas/places/{id}/reviews", reviewsHandler::list)
                .POST("/api/pruebas/places/{id}/reviews", reviewsHandler::create)
                .GET ("/api/pruebas/places/{id}/rating",  reviewsHandler::summary)

                // Feedback
                .POST("/api/pruebas/places/{id}/feedback", feedbackHandler::create)

                .build();
    }
}
