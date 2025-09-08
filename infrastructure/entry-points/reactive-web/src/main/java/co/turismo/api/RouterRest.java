package co.turismo.api;

import co.turismo.api.config.ConstantsEntryPoint;
import co.turismo.api.handler.AuthenticateHandler;
import co.turismo.api.handler.PlacesHandler;
import co.turismo.api.handler.UserHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration
public class RouterRest {
    @Bean
    public RouterFunction<ServerResponse> routerFunction(
            UserHandler userHandler,
            AuthenticateHandler authenticateHandler,
            PlacesHandler placesHandler
    ) {
        return route()
                // Auth
                .POST(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.AUTH_REQUEST_CODE_PATH, authenticateHandler::sendVerificationCode)
                .POST(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.AUTH_VERIFY_CODE_PATH,  authenticateHandler::authenticate)

                // Users
                .POST(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.CREATEUSER, userHandler::createUser)
                .GET(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.INFOUSER, userHandler::getInfoUser)
                .PATCH(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.USERS_ME_PATH, userHandler::updateMyProfile)

                // Places (público autenticado / owners)
                .POST (ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.PLACES_BASE_PATH,     placesHandler::create)
                .GET  (ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.PLACES_NEARBY_PATH,   placesHandler::findNearby)
                .GET  (ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.PLACES_MINE_PATH,     placesHandler::myPlaces)
                .PATCH(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.PLACES_ACTIVE_PATH,   placesHandler::setActive)
                .PATCH(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.PLACES_ID_PATH,       placesHandler::patch)

                // Places - Admin
                .PATCH(ConstantsEntryPoint.API_ADMIN + ConstantsEntryPoint.ADMIN_PLACES_VERIFY_PATH, placesHandler::verify)

                .build();
    }
}
