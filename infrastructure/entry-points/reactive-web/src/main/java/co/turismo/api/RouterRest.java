package co.turismo.api;

import co.turismo.api.config.ConstantsEntryPoint;
import co.turismo.api.config.ScalarDocumentationController;
import co.turismo.api.handler.*;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static org.springdoc.core.fn.builders.content.Builder.contentBuilder;
import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springframework.web.reactive.function.server.RequestPredicates.GET;

@Configuration
public class RouterRest {

    private static final String JSON = "application/json";

    /**
     * Ruta separada para Scalar UI (fuera de Springdoc)
     */
    @Bean
    public RouterFunction<ServerResponse> scalarRoute() {
        ScalarDocumentationController scalarController = new ScalarDocumentationController();

        return RouterFunctions.route(GET("/scalar"), scalarController::serveScalarUI)
                .andRoute(GET("/scalar/"), scalarController::serveScalarUI); // También con slash
    }

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

                // =========================
                // Auth (público)
                // =========================
                .GET(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.AUTH_TOTP_STATUS_PATH,
                        authenticateHandler::totpStatus,
                        ops -> ops.operationId("authTotpStatus")
                                .summary("Estado TOTP por email")
                                .description("Retorna si el usuario ya tiene TOTP habilitado.")
                                .tag("Auth")
                                .parameter(parameterBuilder()
                                        .name("email")
                                        .in(ParameterIn.QUERY)
                                        .required(true)
                                        .description("Email del usuario"))
                                .response(responseBuilder().responseCode("200").description("OK"))
                                .response(responseBuilder().responseCode("400").description("Parámetro inválido"))
                                .response(responseBuilder().responseCode("404").description("Usuario no encontrado"))
                )

                .POST(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.AUTH_TOTP_SETUP_PATH,
                        authenticateHandler::totpSetup,
                        ops -> ops.operationId("authTotpSetup")
                                .summary("Inicia setup TOTP")
                                .description("Genera secreto Base32, URI otpauth y data URL del QR.")
                                .tag("Auth")
                                .requestBody(requestBodyBuilder().required(true).content(
                                        contentBuilder().mediaType(JSON)
                                ))
                                .response(responseBuilder().responseCode("200").description("OK"))
                                .response(responseBuilder().responseCode("409").description("Ya habilitado"))
                                .response(responseBuilder().responseCode("400").description("Error en setup"))
                )

                .POST(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.AUTH_TOTP_CONFIRM_PATH,
                        authenticateHandler::totpConfirm,
                        ops -> ops.operationId("authTotpConfirm")
                                .summary("Confirma TOTP")
                                .description("Verifica el primer código y habilita TOTP para el usuario.")
                                .tag("Auth")
                                .requestBody(requestBodyBuilder().required(true).content(
                                        contentBuilder().mediaType(JSON)
                                ))
                                .response(responseBuilder().responseCode("200").description("Habilitado"))
                                .response(responseBuilder().responseCode("400").description("Código inválido / error"))
                )

                .POST(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.AUTH_LOGIN_TOTP_PATH,
                        authenticateHandler::loginTotp,
                        ops -> ops.operationId("authLoginTotp")
                                .summary("Login con TOTP")
                                .description("Devuelve JWT si la verificación TOTP es correcta.")
                                .tag("Auth")
                                .requestBody(requestBodyBuilder().required(true).content(
                                        contentBuilder().mediaType(JSON)
                                ))
                                .response(responseBuilder().responseCode("200").description("Token emitido"))
                                .response(responseBuilder().responseCode("400").description("Error en login TOTP"))
                )

                .POST(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.AUTH_REFRESH_PATH,
                        authenticateHandler::refresh,
                        ops -> ops.operationId("authRefresh")
                                .summary("Refrescar sesión")
                                .description("Toma token por Authorization Bearer o en body y renueva si aplica.")
                                .tag("Auth")
                                .requestBody(requestBodyBuilder().required(false).content(
                                        contentBuilder().mediaType(JSON)
                                ))
                                .response(responseBuilder().responseCode("200").description("Token renovado"))
                                .response(responseBuilder().responseCode("401").description("No autorizado / fuera de ventana"))
                )

                // =========================
                // Users (protegido)
                // =========================
                .POST(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.CREATEUSER,
                        userHandler::createUser,
                        ops -> ops.operationId("userCreate")
                                .summary("Crear usuario")
                                .tag("Users")
                                .requestBody(requestBodyBuilder().required(true).content(
                                        contentBuilder().mediaType(JSON)
                                ))
                                .response(responseBuilder().responseCode("200").description("Creado"))
                                .response(responseBuilder().responseCode("400").description("Datos inválidos"))
                )

                .GET(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.INFOUSER,
                        userHandler::getInfoUser,
                        ops -> ops.operationId("userInfo")
                                .summary("Mi información")
                                .tag("Users")
                                .response(responseBuilder().responseCode("200").description("OK"))
                )

                .GET(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.ALLUSER,
                        userHandler::getAllUsers,
                        ops -> ops.operationId("userList")
                                .summary("Listado de usuarios")
                                .tag("Users")
                                .response(responseBuilder().responseCode("200").description("OK"))
                )

                .PATCH(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.USERS_ME_PATH,
                        userHandler::updateMyProfile,
                        ops -> ops.operationId("userUpdateMe")
                                .summary("Actualizar mi perfil")
                                .tag("Users")
                                .requestBody(requestBodyBuilder().required(true).content(
                                        contentBuilder().mediaType(JSON)
                                ))
                                .response(responseBuilder().responseCode("200").description("Actualizado"))
                )

                // =========================
                // Places
                // =========================
                .POST(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.PLACES_BASE_PATH,
                        placesHandler::create,
                        ops -> ops.operationId("placeCreate")
                                .summary("Crear lugar")
                                .description("Requiere rol OWNER o permisos.")
                                .tag("Places")
                                .requestBody(requestBodyBuilder().required(true).content(
                                        contentBuilder().mediaType(JSON)
                                ))
                                .response(responseBuilder().responseCode("200").description("Creado"))
                                .response(responseBuilder().responseCode("400").description("Datos inválidos"))
                )

                .GET(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.PLACES_NEARBY_PATH,
                        placesHandler::findNearby,
                        ops -> ops.operationId("placesNearby")
                                .summary("Lugares cercanos")
                                .description("Filtra por lat,lng,radius,categoryId, etc.")
                                .tag("Places")
                                .parameter(parameterBuilder().name("lat").in(ParameterIn.QUERY).required(true))
                                .parameter(parameterBuilder().name("lng").in(ParameterIn.QUERY).required(true))
                                .parameter(parameterBuilder().name("radius").in(ParameterIn.QUERY).required(false))
                                .parameter(parameterBuilder().name("categoryId").in(ParameterIn.QUERY).required(false))
                                .response(responseBuilder().responseCode("200").description("OK"))
                )

                .GET(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.PLACES_SEARCH_PATH,
                        placesHandler::search,
                        ops -> ops.operationId("placesSearch")
                                .summary("Buscar lugares por texto")
                                .tag("Places")
                                .parameter(parameterBuilder().name("q").in(ParameterIn.QUERY).required(true).description("Texto de búsqueda"))
                                .response(responseBuilder().responseCode("200").description("OK"))
                )

                .GET(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.PLACES_ALL_PATH,
                        placesHandler::findAllPlaces,
                        ops -> ops.operationId("placesAll")
                                .summary("Todos los lugares (paginado)")
                                .tag("Places")
                                .parameter(parameterBuilder().name("page").in(ParameterIn.QUERY).required(false))
                                .parameter(parameterBuilder().name("size").in(ParameterIn.QUERY).required(false))
                                .response(responseBuilder().responseCode("200").description("OK"))
                )

                .GET(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.PLACES_MINE_PATH,
                        placesHandler::myPlaces,
                        ops -> ops.operationId("placesMine")
                                .summary("Mis lugares (OWNER)")
                                .tag("Places")
                                .response(responseBuilder().responseCode("200").description("OK"))
                )

                .GET(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.PLACES_ID_PATH,
                        placesHandler::findByIdPlace,
                        ops -> ops.operationId("placeById")
                                .summary("Obtener lugar por ID")
                                .tag("Places")
                                .parameter(parameterBuilder().name("id").in(ParameterIn.PATH).required(true))
                                .response(responseBuilder().responseCode("200").description("OK"))
                                .response(responseBuilder().responseCode("404").description("No encontrado"))
                )

                .PATCH(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.PLACES_ACTIVE_PATH,
                        placesHandler::setActive,
                        ops -> ops.operationId("placeSetActive")
                                .summary("Activar/Desactivar lugar")
                                .tag("Places")
                                .requestBody(requestBodyBuilder().required(true).content(
                                        contentBuilder().mediaType(JSON)
                                ))
                                .response(responseBuilder().responseCode("200").description("OK"))
                )

                .PATCH(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.PLACES_ID_PATH,
                        placesHandler::patch,
                        ops -> ops.operationId("placePatch")
                                .summary("Patch lugar por ID")
                                .tag("Places")
                                .parameter(parameterBuilder().name("id").in(ParameterIn.PATH).required(true))
                                .requestBody(requestBodyBuilder().required(true))
                                .response(responseBuilder().responseCode("200").description("OK"))
                )

                .DELETE(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.PLACES_ID_PATH,
                        placesHandler::delete,
                        ops -> ops.operationId("placeDelete")
                                .summary("Eliminar lugar por ID")
                                .tag("Places")
                                .parameter(parameterBuilder().name("id").in(ParameterIn.PATH).required(true))
                                .response(responseBuilder().responseCode("200").description("Eliminado"))
                )

                // Debug
                .GET(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.DEBUG,
                        placesHandler::debug,
                        ops -> ops.operationId("placesDebug")
                                .summary("Debug places")
                                .tag("Places")
                                .response(responseBuilder().responseCode("200").description("OK"))
                )

                // =========================
                // Places - Admin
                // =========================
                .PATCH(ConstantsEntryPoint.API_ADMIN + ConstantsEntryPoint.ADMIN_PLACES_VERIFY_PATH,
                        placesHandler::verify,
                        ops -> ops.operationId("adminPlaceVerify")
                                .summary("Verificar lugar (ADMIN)")
                                .tag("Admin/Places")
                                .parameter(parameterBuilder().name("id").in(ParameterIn.PATH).required(true))
                                .requestBody(requestBodyBuilder().required(true))
                                .response(responseBuilder().responseCode("200").description("OK"))
                                .response(responseBuilder().responseCode("403").description("Forbidden"))
                )

                // =========================
                // Geocode & Visits (pruebas)
                // =========================
                .POST(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.GETCOORDINATES,
                        geocodeHandler::geocode,
                        ops -> ops.operationId("geocode")
                                .summary("Geocodificar dirección")
                                .tag("Geocode")
                                .requestBody(requestBodyBuilder().required(true).content(
                                        contentBuilder().mediaType(JSON)
                                ))
                                .response(responseBuilder().responseCode("200").description("OK"))
                )

                .GET(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.PLACES_NEARBY_PATH_PLACE,
                        visitHandler::nearby,
                        ops -> ops.operationId("visitsNearbyPlaces")
                                .summary("Lugares cercanos (contexto visitas)")
                                .tag("Visits")
                                .parameter(parameterBuilder().name("lat").in(ParameterIn.QUERY).required(true))
                                .parameter(parameterBuilder().name("lng").in(ParameterIn.QUERY).required(true))
                                .response(responseBuilder().responseCode("200").description("OK"))
                )

                .POST(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.VISITS_CHECKIN_PATH,
                        visitHandler::checkin,
                        ops -> ops.operationId("visitsCheckin")
                                .summary("Check-in en lugar")
                                .tag("Visits")
                                .parameter(parameterBuilder().name("placeId").in(ParameterIn.PATH).required(true))
                                .requestBody(requestBodyBuilder().required(true))
                                .response(responseBuilder().responseCode("200").description("OK"))
                )

                .PATCH(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.VISITS_CONFIRM_PATH,
                        visitHandler::confirm,
                        ops -> ops.operationId("visitsConfirm")
                                .summary("Confirmar visita")
                                .tag("Visits")
                                .parameter(parameterBuilder().name("visitId").in(ParameterIn.PATH).required(true))
                                .requestBody(requestBodyBuilder().required(true))
                                .response(responseBuilder().responseCode("200").description("OK"))
                )

                .GET(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.ANALYTICS_TOP_PLACES_PATH,
                        visitHandler::topPlaces,
                        ops -> ops.operationId("analyticsTopPlaces")
                                .summary("Top lugares más visitados")
                                .tag("Analytics")
                                .parameter(parameterBuilder().name("limit").in(ParameterIn.QUERY).required(false))
                                .response(responseBuilder().responseCode("200").description("OK"))
                )

                // =========================
                // Reviews & Feedback (pruebas)
                // =========================
                .GET(ConstantsEntryPoint.API_BASE_PATH + "/pruebas/places/{id}/reviews",
                        reviewsHandler::list,
                        ops -> ops.operationId("reviewsList")
                                .summary("Listar reviews por lugar")
                                .tag("Reviews")
                                .parameter(parameterBuilder().name("id").in(ParameterIn.PATH).required(true))
                                .response(responseBuilder().responseCode("200").description("OK"))
                )

                .POST(ConstantsEntryPoint.API_BASE_PATH + "/pruebas/places/{id}/reviews",
                        reviewsHandler::create,
                        ops -> ops.operationId("reviewsCreate")
                                .summary("Crear review")
                                .tag("Reviews")
                                .parameter(parameterBuilder().name("id").in(ParameterIn.PATH).required(true))
                                .requestBody(requestBodyBuilder().required(true))
                                .response(responseBuilder().responseCode("200").description("OK"))
                )

                .GET(ConstantsEntryPoint.API_BASE_PATH + "/pruebas/places/{id}/rating",
                        reviewsHandler::summary,
                        ops -> ops.operationId("reviewsSummary")
                                .summary("Resumen de rating")
                                .tag("Reviews")
                                .parameter(parameterBuilder().name("id").in(ParameterIn.PATH).required(true))
                                .response(responseBuilder().responseCode("200").description("OK"))
                )

                .POST(ConstantsEntryPoint.API_BASE_PATH + "/pruebas/places/{id}/feedback",
                        feedbackHandler::create,
                        ops -> ops.operationId("feedbackCreate")
                                .summary("Crear feedback para un lugar")
                                .tag("Feedback")
                                .parameter(parameterBuilder().name("id").in(ParameterIn.PATH).required(true))
                                .requestBody(requestBodyBuilder().required(true))
                                .response(responseBuilder().responseCode("200").description("OK"))
                )

                .build();
    }
}