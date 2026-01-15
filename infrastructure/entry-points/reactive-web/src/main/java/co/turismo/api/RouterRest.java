package co.turismo.api;

import co.turismo.api.config.ConstantsEntryPoint;
import co.turismo.api.dto.auth.RefreshTokenRequest;
import co.turismo.api.dto.auth.TotpConfirmRequest;
import co.turismo.api.dto.auth.TotpEmailRequest;
import co.turismo.api.dto.auth.TotpLoginRequest;
import co.turismo.api.dto.common.SimpleMessageResponse;
import co.turismo.api.dto.response.docs.*;
import co.turismo.api.dto.visit.CheckinRequest;
import co.turismo.api.dto.visit.ConfirmRequest;
import co.turismo.api.handler.*;
import co.turismo.api.handler.PlacesHandler.ActiveRequest;
import co.turismo.api.handler.PlacesHandler.PlaceCreateRequest;
import co.turismo.api.handler.PlacesHandler.UpdateRequest;
import co.turismo.api.handler.PlacesHandler.VerifyRequest;
import co.turismo.model.feedback.Feedback;
import co.turismo.model.place.Place;
import co.turismo.model.reviews.Review;
import co.turismo.model.user.UpdateUserProfileRequest;
import co.turismo.model.user.User;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.content.Builder.contentBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static org.springdoc.core.fn.builders.schema.Builder.schemaBuilder;
import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;

@Configuration
public class RouterRest {

    private static final String JSON = "application/json";

    @Bean
    public RouterFunction<ServerResponse> routerFunction(
            UserHandler userHandler,
            AuthenticateHandler authenticateHandler,
            PlacesHandler placesHandler,
            VisitHandler visitHandler,
            GeocodeHandler geocodeHandler,
            ReviewsHandler reviewsHandler,
            FeedbackHandler feedbackHandler,
            TourPackageHandler tourPackageHandler,
            AgencyHandler agencyHandler,
            CategoryHandler categoryHandler
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
                                .parameter(queryParam("email", true, "Email del usuario", String.class, "ana@example.com"))
                                .response(jsonResponse("200", "Estado actual de TOTP", ApiTotpStatusResponse.class))
                                .response(messageResponse("400", "Parámetro inválido"))
                                .response(messageResponse("404", "Usuario no encontrado"))
                )

                .POST(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.AUTH_TOTP_SETUP_PATH,
                        authenticateHandler::totpSetup,
                        ops -> ops.operationId("authTotpSetup")
                                .summary("Inicia setup TOTP")
                                .description("Genera secreto Base32, URI otpauth y data URL del QR.")
                                .tag("Auth")
                                .requestBody(jsonBody(TotpEmailRequest.class, true))
                                .response(jsonResponse("200", "Secreto generado", ApiTotpSetupResponse.class))
                                .response(messageResponse("409", "Ya fue habilitado anteriormente"))
                                .response(messageResponse("400", "Error en el proceso de setup"))
                )

                .POST(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.AUTH_TOTP_CONFIRM_PATH,
                        authenticateHandler::totpConfirm,
                        ops -> ops.operationId("authTotpConfirm")
                                .summary("Confirma TOTP")
                                .description("Verifica el primer código y habilita TOTP para el usuario.")
                                .tag("Auth")
                                .requestBody(jsonBody(TotpConfirmRequest.class, true))
                                .response(jsonResponse("200", "TOTP habilitado", ApiMessageResponse.class))
                                .response(messageResponse("400", "Código inválido o expirado"))
                )

                .POST(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.AUTH_LOGIN_TOTP_PATH,
                        authenticateHandler::loginTotp,
                        ops -> ops.operationId("authLoginTotp")
                                .summary("Login con TOTP")
                                .description("Devuelve JWT si la verificación TOTP es correcta.")
                                .tag("Auth")
                                .requestBody(jsonBody(TotpLoginRequest.class, true))
                                .response(jsonResponse("200", "Token emitido", ApiTokenResponse.class))
                                .response(messageResponse("400", "Error en login TOTP"))
                )

                .POST(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.AUTH_REFRESH_PATH,
                        authenticateHandler::refresh,
                        ops -> ops.operationId("authRefresh")
                                .summary("Refrescar sesión")
                                .description("Toma token por Authorization Bearer o en body y renueva si aplica.")
                                .tag("Auth")
                                .parameter(headerParam("Authorization", false, "Bearer token que se desea refrescar", "Bearer eyJhbGciOiJIUzI1NiJ9..."))
                                .requestBody(jsonBody(RefreshTokenRequest.class, false))
                                .response(jsonResponse("200", "Token renovado", ApiTokenResponse.class))
                                .response(messageResponse("401", "No autorizado o fuera de la ventana de gracia"))
                )

                // =========================
                // Users (protegido)
                // =========================
                .POST(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.CREATEUSER,
                        userHandler::createUser,
                        ops -> ops.operationId("userCreate")
                                .summary("Crear usuario")
                                .tag("Users")
                                .requestBody(jsonBody(User.class, true))
                                .response(jsonResponse("200", "Usuario creado", ApiUserResponse.class))
                                .response(apiErrorResponse("400", "Datos inválidos"))
                )

                .GET(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.INFOUSER,
                        userHandler::getInfoUser,
                        ops -> ops.operationId("userInfo")
                                .summary("Mi información")
                                .tag("Users")
                                .parameter(queryParam("userEmail", true, "Email a consultar", String.class, "ana@example.com"))
                                .response(jsonResponse("200", "Información del usuario", User.class))
                )

                .GET(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.ALLUSER,
                        userHandler::getAllUsers,
                        ops -> ops.operationId("userList")
                                .summary("Listado de usuarios")
                                .tag("Users")
                                .response(jsonArrayResponse("200", "Usuarios registrados", User[].class))
                )

                .PATCH(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.USERS_ME_PATH,
                        userHandler::updateMyProfile,
                        ops -> ops.operationId("userUpdateMe")
                                .summary("Actualizar mi perfil")
                                .tag("Users")
                                .requestBody(jsonBody(UpdateUserProfileRequest.class, true))
                                .response(jsonResponse("200", "Perfil actualizado", ApiUserResponse.class))
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
                                .requestBody(jsonBody(PlaceCreateRequest.class, true))
                                .response(jsonResponse("201", "Lugar creado", ApiPlaceResponse.class))
                                .response(apiErrorResponse("400", "Datos inválidos"))
                )

                .GET(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.PLACES_NEARBY_PATH,
                        placesHandler::findNearby,
                        ops -> ops.operationId("placesNearby")
                                .summary("Lugares cercanos")
                                .description("Filtra por coordenadas, radio y categoría.")
                                .tag("Places")
                                .parameter(queryParam("lat", true, "Latitud del usuario", Double.class, "6.25184"))
                                .parameter(queryParam("lng", true, "Longitud del usuario", Double.class, "-75.56359"))
                                .parameter(queryParam("radiusMeters", false, "Radio en metros (alias: r)", Double.class, "1000"))
                                .parameter(queryParam("limit", false, "Número máximo de resultados", Integer.class, "20"))
                                .parameter(queryParam("categoryId", false, "Filtra por categoría", Long.class, "5"))
                                .response(jsonResponse("200", "Resultados por cercanía", ApiPlaceListResponse.class))
                )

                .GET(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.PLACES_SEARCH_PATH,
                        placesHandler::search,
                        ops -> ops.operationId("placesSearch")
                                .summary("Buscar lugares por texto")
                                .tag("Places")
                                .parameter(queryParam("q", false, "Texto de búsqueda libre", String.class, "caf\u00e9"))
                                .parameter(queryParam("categoryId", false, "Categoría a filtrar", Long.class, "5"))
                                .parameter(queryParam("onlyNearby", false, "Limita a un radio respecto a lat/lng", Boolean.class, "false"))
                                .parameter(queryParam("lat", false, "Latitud para onlyNearby", Double.class, "6.25"))
                                .parameter(queryParam("lng", false, "Longitud para onlyNearby", Double.class, "-75.56"))
                                .parameter(queryParam("radiusMeters", false, "Radio en metros para onlyNearby", Double.class, "1000"))
                                .parameter(queryParam("page", false, "Página (0..N)", Integer.class, "0"))
                                .parameter(queryParam("size", false, "Cantidad por página (<=100)", Integer.class, "20"))
                                .response(jsonResponse("200", "Coincidencias por texto", ApiPlaceListResponse.class))
                )

                .GET(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.PLACES_ALL_PATH,
                        placesHandler::findAllPlaces,
                        ops -> ops.operationId("placesAll")
                                .summary("Todos los lugares")
                                .description("Retorna el catálogo completo sin envoltorio paginado.")
                                .tag("Places")
                                .response(jsonArrayResponse("200", "Listado completo", Place[].class))
                )

                .GET(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.PLACES_MINE_PATH,
                        placesHandler::myPlaces,
                        ops -> ops.operationId("placesMine")
                                .summary("Mis lugares (OWNER)")
                                .tag("Places")
                                .response(jsonResponse("200", "Lugares del propietario autenticado", ApiPlaceListResponse.class))
                )

                .GET(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.PLACES_ID_PATH,
                        placesHandler::findByIdPlace,
                        ops -> ops.operationId("placeById")
                                .summary("Obtener lugar por ID")
                                .tag("Places")
                                .parameter(pathParam("id", "Identificador interno del lugar", Long.class))
                                .response(jsonResponse("200", "Lugar encontrado", Place.class))
                                .response(apiErrorResponse("404", "No encontrado"))
                )

                .PATCH(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.PLACES_ACTIVE_PATH,
                        placesHandler::setActive,
                        ops -> ops.operationId("placeSetActive")
                                .summary("Activar/Desactivar lugar")
                                .tag("Places")
                                .parameter(pathParam("id", "Identificador interno del lugar", Long.class))
                                .requestBody(jsonBody(ActiveRequest.class, true))
                                .response(jsonResponse("200", "Estado actualizado", ApiPlaceResponse.class))
                )

                .PATCH(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.PLACES_ID_PATH,
                        placesHandler::patch,
                        ops -> ops.operationId("placePatch")
                                .summary("Patch lugar por ID")
                                .tag("Places")
                                .parameter(pathParam("id", "Identificador interno del lugar", Long.class))
                                .requestBody(jsonBody(UpdateRequest.class, true))
                                .response(jsonResponse("200", "Lugar actualizado", ApiPlaceResponse.class))
                )

                .DELETE(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.PLACES_ID_PATH,
                        placesHandler::delete,
                        ops -> ops.operationId("placeDelete")
                                .summary("Eliminar lugar por ID")
                                .tag("Places")
                                .parameter(pathParam("id", "Identificador interno del lugar", Long.class))
                                .response(jsonResponse("200", "Eliminado", ApiBooleanResponse.class))
                                .response(apiErrorResponse("403", "No es el propietario"))
                                .response(apiErrorResponse("404", "Lugar o usuario no encontrado"))
                )

                // Debug
                .GET(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.DEBUG,
                        placesHandler::debug,
                        ops -> ops.operationId("placesDebug")
                                .summary("Debug places")
                                .tag("Places")
                                .response(jsonResponse("200", "Información del contexto autenticado", String.class))
                )

                // =========================
                // Agencies
                // =========================
                .POST(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.AGENCIES_BASE_PATH,
                        agencyHandler::create,
                        ops -> ops.operationId("agencyCreate")
                                .summary("Crear agencia")
                                .tag("Agencies")
                                .requestBody(jsonBody(AgencyHandler.CreateAgencyBody.class, true))
                                .response(jsonResponse("201", "Agencia creada", ApiAgencyResponse.class))
                                .response(apiErrorResponse("400", "Datos inválidos"))
                )

                .POST(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.AGENCIES_USERS_PATH,
                        agencyHandler::addUser,
                        ops -> ops.operationId("agencyAddUser")
                                .summary("Agregar usuario a mi agencia")
                                .tag("Agencies")
                                .requestBody(jsonBody(AgencyHandler.AddAgencyUserBody.class, true))
                                .response(jsonResponse("200", "Usuario asociado", ApiAgencyResponse.class))
                                .response(apiErrorResponse("400", "Datos inválidos"))
                )

                .GET(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.AGENCIES_BASE_PATH,
                        agencyHandler::list,
                        ops -> ops.operationId("agencyList")
                                .summary("Listado de agencias")
                                .tag("Agencies")
                                .response(jsonResponse("200", "Listado de agencias", ApiAgencyListResponse.class))
                )

                .GET(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.AGENCIES_BY_USER_PATH,
                        agencyHandler::findByUser,
                        ops -> ops.operationId("agencyByUser")
                                .summary("Consultar agencia por email")
                                .tag("Agencies")
                                .parameter(queryParam("email", false, "Email del usuario", String.class, "ana@example.com"))
                                .response(jsonResponse("200", "Agencia encontrada", ApiAgencyResponse.class))
                                .response(apiErrorResponse("404", "No encontrado"))
                )

                .GET(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.AGENCIES_DASHBOARD_PATH,
                        agencyHandler::dashboard,
                        ops -> ops.operationId("agencyDashboard")
                                .summary("Dashboard de agencia")
                                .tag("Agencies")
                                .parameter(queryParam("email", false, "Email del usuario", String.class, "ana@example.com"))
                                .parameter(queryParam("from", false, "Fecha inicial (yyyy-MM-dd)", String.class, "2024-01-01"))
                                .parameter(queryParam("to", false, "Fecha final (yyyy-MM-dd)", String.class, "2024-01-31"))
                                .parameter(queryParam("limit", false, "Cantidad de resultados por ranking", Integer.class, "10"))
                                .response(jsonResponse("200", "Dashboard de agencia", ApiAgencyDashboardResponse.class))
                                .response(apiErrorResponse("404", "No encontrado"))
                )

                // =========================
                // Tour Packages
                // =========================
                .POST(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.PACKAGES_BASE_PATH,
                        tourPackageHandler::create,
                        ops -> ops.operationId("packageCreate")
                                .summary("Crear paquete turístico")
                                .tag("Packages")
                                .requestBody(jsonBody(TourPackageHandler.CreatePackageRequest.class, true))
                                .response(jsonResponse("201", "Paquete creado", ApiTourPackageResponse.class))
                                .response(apiErrorResponse("400", "Datos inválidos"))
                )

                .GET(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.PACKAGES_BASE_PATH,
                        tourPackageHandler::list,
                        ops -> ops.operationId("packagesList")
                                .summary("Listado de paquetes turísticos")
                                .tag("Packages")
                                .response(jsonResponse("200", "Listado de paquetes", ApiTourPackageListResponse.class))
                )

                .GET(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.PACKAGES_ID_PATH,
                        tourPackageHandler::findById,
                        ops -> ops.operationId("packageById")
                                .summary("Obtener paquete turístico por ID")
                                .tag("Packages")
                                .parameter(pathParam("id", "Identificador interno del paquete", Long.class))
                                .response(jsonResponse("200", "Paquete encontrado", ApiTourPackageResponse.class))
                                .response(apiErrorResponse("404", "No encontrado"))
                )

                // =========================
                // Categories
                // =========================
                .GET(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.CATEGORIES_BASE_PATH,
                        categoryHandler::list,
                        ops -> ops.operationId("categoryList")
                                .summary("Listado de categorías")
                                .tag("Categories")
                                .response(jsonResponse("200", "Listado de categorías", ApiCategoryListResponse.class))
                )

                .GET(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.CATEGORIES_ID_PATH,
                        categoryHandler::findById,
                        ops -> ops.operationId("categoryById")
                                .summary("Obtener categoría por ID")
                                .tag("Categories")
                                .parameter(pathParam("id", "Identificador interno de la categoría", Long.class))
                                .response(jsonResponse("200", "Categoría encontrada", ApiCategoryResponse.class))
                                .response(apiErrorResponse("404", "No encontrado"))
                )

                .POST(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.CATEGORIES_BASE_PATH,
                        categoryHandler::create,
                        ops -> ops.operationId("categoryCreate")
                                .summary("Crear categoría (ADMIN)")
                                .tag("Categories")
                                .requestBody(jsonBody(CategoryHandler.CreateCategoryBody.class, true))
                                .response(jsonResponse("201", "Categoría creada", ApiCategoryResponse.class))
                                .response(apiErrorResponse("400", "Datos inválidos"))
                )

                .PATCH(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.CATEGORIES_ID_PATH,
                        categoryHandler::update,
                        ops -> ops.operationId("categoryUpdate")
                                .summary("Editar categoría (ADMIN)")
                                .tag("Categories")
                                .parameter(pathParam("id", "Identificador interno de la categoría", Long.class))
                                .requestBody(jsonBody(CategoryHandler.UpdateCategoryBody.class, true))
                                .response(jsonResponse("200", "Categoría actualizada", ApiCategoryResponse.class))
                                .response(apiErrorResponse("400", "Datos inválidos"))
                                .response(apiErrorResponse("404", "No encontrado"))
                )

                // =========================
                // Places - Admin
                // =========================
                .PATCH(ConstantsEntryPoint.API_ADMIN + ConstantsEntryPoint.ADMIN_PLACES_VERIFY_PATH,
                        placesHandler::verify,
                        ops -> ops.operationId("adminPlaceVerify")
                                .summary("Verificar lugar (ADMIN)")
                                .tag("Admin/Places")
                                .parameter(pathParam("id", "Identificador interno del lugar", Long.class))
                                .requestBody(jsonBody(VerifyRequest.class, true))
                                .response(jsonResponse("200", "Lugar verificado", ApiPlaceResponse.class))
                                .response(apiErrorResponse("403", "Forbidden"))
                )

                // =========================
                // Geocode & Visits (pruebas)
                // =========================
                .POST(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.GETCOORDINATES,
                        geocodeHandler::geocode,
                        ops -> ops.operationId("geocode")
                                .summary("Geocodificar dirección")
                                .tag("Geocode")
                                .requestBody(jsonBody(GeocodeHandler.Req.class, true))
                                .response(jsonResponse("200", "Resultados de geocodificación", ApiGeocodeResponse.class))
                )

                .GET(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.PLACES_NEARBY_PATH_PLACE,
                        visitHandler::nearby,
                        ops -> ops.operationId("visitsNearbyPlaces")
                                .summary("Lugares cercanos (contexto visitas)")
                                .tag("Visits")
                                .parameter(queryParam("lat", true, "Latitud del usuario", Double.class, "6.25184"))
                                .parameter(queryParam("lng", true, "Longitud del usuario", Double.class, "-75.56359"))
                                .parameter(queryParam("radius", false, "Radio máximo en metros", Integer.class, "150"))
                                .parameter(queryParam("limit", false, "Cantidad máxima de lugares", Integer.class, "5"))
                                .response(jsonResponse("200", "Sugerencias cercanas", ApiNearbyPlacesResponse.class))
                )

                .POST(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.VISITS_CHECKIN_PATH,
                        visitHandler::checkin,
                        ops -> ops.operationId("visitsCheckin")
                                .summary("Check-in en lugar")
                                .tag("Visits")
                                .parameter(pathParam("placeId", "Identificador del lugar visitado", Long.class))
                                .requestBody(jsonBody(CheckinRequest.class, true))
                                .response(jsonResponse("200", "Check-in registrado", ApiCheckinResponse.class))
                                .response(apiErrorResponse("400", "Solicitud inválida"))
                                .response(apiErrorResponse("409", "Conflicto con otro check-in"))
                )

                .PATCH(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.VISITS_CONFIRM_PATH,
                        visitHandler::confirm,
                        ops -> ops.operationId("visitsConfirm")
                                .summary("Confirmar visita")
                                .tag("Visits")
                                .parameter(pathParam("visitId", "Identificador del check-in", Long.class))
                                .requestBody(jsonBody(ConfirmRequest.class, true))
                                .response(jsonResponse("200", "Visita confirmada", ApiConfirmResponse.class))
                                .response(apiErrorResponse("400", "Datos inválidos"))
                                .response(apiErrorResponse("409", "No cumple los criterios de confirmación"))
                )

                .GET(ConstantsEntryPoint.API_BASE_PATH + ConstantsEntryPoint.ANALYTICS_TOP_PLACES_PATH,
                        visitHandler::topPlaces,
                        ops -> ops.operationId("analyticsTopPlaces")
                                .summary("Top lugares más visitados")
                                .tag("Analytics")
                                .parameter(queryParam("from", false, "Fecha inicial (yyyy-MM-dd)", String.class, "2024-01-01"))
                                .parameter(queryParam("to", false, "Fecha final (yyyy-MM-dd)", String.class, "2024-01-31"))
                                .parameter(queryParam("limit", false, "Cantidad de resultados", Integer.class, "20"))
                                .response(jsonResponse("200", "Ranking de visitas", ApiTopPlacesResponse.class))
                )

                // =========================
                // Reviews & Feedback
                // =========================
                .GET(ConstantsEntryPoint.API_BASE_PATH + "/pruebas/places/{id}/reviews",
                        reviewsHandler::list,
                        ops -> ops.operationId("reviewsList")
                                .summary("Listar reviews por lugar")
                                .tag("Reviews")
                                .parameter(pathParam("id", "Identificador del lugar", Long.class))
                                .response(jsonArrayResponse("200", "Reseñas del lugar", Review[].class))
                )

                .POST(ConstantsEntryPoint.API_BASE_PATH + "/pruebas/places/{id}/reviews",
                        reviewsHandler::create,
                        ops -> ops.operationId("reviewsCreate")
                                .summary("Crear review")
                                .tag("Reviews")
                                .parameter(pathParam("id", "Identificador del lugar", Long.class))
                                .requestBody(jsonBody(ReviewsHandler.CreateReviewBody.class, true))
                                .response(jsonResponse("201", "Reseña creada", Review.class))
                                .response(apiErrorResponse("400", "Datos inválidos"))
                )

                .GET(ConstantsEntryPoint.API_BASE_PATH + "/pruebas/places/{id}/rating",
                        reviewsHandler::summary,
                        ops -> ops.operationId("reviewsSummary")
                                .summary("Resumen de rating")
                                .tag("Reviews")
                                .parameter(pathParam("id", "Identificador del lugar", Long.class))
                                .response(jsonResponse("200", "Estadísticas de calificación", ReviewsHandler.RatingSummaryResponse.class))
                )

                .POST(ConstantsEntryPoint.API_BASE_PATH + "/pruebas/places/{id}/feedback",
                        feedbackHandler::create,
                        ops -> ops.operationId("feedbackCreate")
                                .summary("Crear feedback para un lugar")
                                .tag("Feedback")
                                .parameter(pathParam("id", "Identificador del lugar", Long.class))
                                .requestBody(jsonBody(FeedbackHandler.CreateFeedbackBody.class, true))
                                .response(jsonResponse("201", "Feedback almacenado", Feedback.class))
                                .response(apiErrorResponse("400", "Solicitud inválida"))
                )

                .build();
    }

    private static org.springdoc.core.fn.builders.apiresponse.Builder jsonResponse(String code, String description, Class<?> schemaClass) {
        var builder = responseBuilder()
                .responseCode(code)
                .description(description);
        if (schemaClass != null) {
            builder.content(jsonContent(schemaClass));
        }
        return builder;
    }

    private static org.springdoc.core.fn.builders.apiresponse.Builder jsonArrayResponse(String code, String description, Class<?> arrayClass) {
        return responseBuilder()
                .responseCode(code)
                .description(description)
                .content(jsonContent(arrayClass));
    }

    private static org.springdoc.core.fn.builders.apiresponse.Builder messageResponse(String code, String description) {
        return responseBuilder()
                .responseCode(code)
                .description(description)
                .content(jsonContent(SimpleMessageResponse.class));
    }

    private static org.springdoc.core.fn.builders.apiresponse.Builder apiErrorResponse(String code, String description) {
        return jsonResponse(code, description, ApiErrorResponse.class);
    }

    private static org.springdoc.core.fn.builders.requestbody.Builder jsonBody(Class<?> schemaClass, boolean required) {
        var builder = requestBodyBuilder().required(required);
        if (schemaClass != null) {
            builder.content(jsonContent(schemaClass));
        }
        return builder;
    }

    private static org.springdoc.core.fn.builders.parameter.Builder queryParam(String name, boolean required, String description, Class<?> schemaClass, String example) {
        var builder = parameterBuilder()
                .name(name)
                .in(ParameterIn.QUERY)
                .required(required);
        if (description != null) {
            builder.description(description);
        }
        if (schemaClass != null) {
            builder.schema(schemaBuilder().implementation(schemaClass));
        }
        if (example != null) {
            builder.example(example);
        }
        return builder;
    }

    private static org.springdoc.core.fn.builders.parameter.Builder pathParam(String name, String description, Class<?> schemaClass) {
        var builder = parameterBuilder()
                .name(name)
                .in(ParameterIn.PATH)
                .required(true);
        if (description != null) {
            builder.description(description);
        }
        if (schemaClass != null) {
            builder.schema(schemaBuilder().implementation(schemaClass));
        }
        return builder;
    }

    private static org.springdoc.core.fn.builders.parameter.Builder headerParam(String name, boolean required, String description, String example) {
        var builder = parameterBuilder()
                .name(name)
                .in(ParameterIn.HEADER)
                .required(required);
        if (description != null) {
            builder.description(description);
        }
        if (example != null) {
            builder.example(example);
        }
        return builder;
    }

    private static org.springdoc.core.fn.builders.content.Builder jsonContent(Class<?> schemaClass) {
        var builder = contentBuilder().mediaType(JSON);
        if (schemaClass != null) {
            builder.schema(schemaBuilder().implementation(schemaClass));
        }
        return builder;
    }
}
