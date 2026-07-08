package co.turismo.api.config;

public class ConstantsEntryPoint {
    private ConstantsEntryPoint() {}

    public static final String API_BASE_PATH = "/api";
    public static final String API_ADMIN     = "/admin";

    // Auth
    public static final String AUTH_TOTP_SETUP_PATH   = "/auth/code/setup";
    public static final String AUTH_TOTP_CONFIRM_PATH = "/auth/code/confirm";
    public static final String AUTH_LOGIN_TOTP_PATH   = "/auth/login-code";
    public static final String AUTH_TOTP_STATUS_PATH = "/auth/code/status";
    public static final String AUTH_REFRESH_PATH = "/auth/refresh";
    public static final String AUTH_LOGOUT_PATH = "/auth/logout";
    public static final String AUTH_LOGIN_PASSWORD_PATH = "/auth/login-password";
    public static final String AUTH_EMAIL_REQUEST_PATH = "/auth/email/request";
    public static final String AUTH_EMAIL_VERIFY_PATH = "/auth/email/verify";
    public static final String AUTH_RECOVERY_REQUEST_PATH = "/auth/recovery/request";
    public static final String AUTH_RECOVERY_CONFIRM_PATH = "/auth/recovery/confirm";

    // Users
    public static final String CREATEUSER = "/auth/create/user";
    public static final String INFOUSER = "/info/user";
    public static final String ALLUSER = "/admin/all/user";
    public static final String USERS_ME_PATH = "/users/me";
    public static final String USERS_ME_PASSWORD_PATH = "/users/me/password";

    // Places
    public static final String PLACES_BASE_PATH    = "/places";
    public static final String PLACES_NEARBY_PATH  = "/places/nearby";
    public static final String PLACES_SEARCH_FILTER_PATH = "/places/search";
    public static final String PLACES_ALL_PATH  = "/places/all";
    public static final String PLACES_MINE_PATH    = "/places/mine";
    public static final String PLACES_ACTIVE_PATH  = "/places/{id}/active";
    public static final String PLACES_ID_PATH = "/places/{id}";

    // Tour packages
    public static final String PACKAGES_BASE_PATH = "/packages";
    public static final String PACKAGES_ID_PATH = "/packages/{id}";

    // Reservations
    public static final String RESERVATIONS_BASE_PATH = "/reservations";
    public static final String RESERVATIONS_ME_PATH = "/reservations/me";
    public static final String RESERVATIONS_ID_PATH = "/reservations/{reservationId}";
    public static final String RESERVATIONS_MESSAGES_PATH = "/reservations/{reservationId}/messages";
    public static final String RESERVATIONS_PAYMENT_CHECKOUT_PATH = "/reservations/{reservationId}/payment/checkout";
    public static final String RESERVATIONS_PAYMENT_CHECKOUT_PAGE_PATH = "/reservations/{reservationId}/payment/checkout-page";
    public static final String RESERVATIONS_PAYMENT_STATUS_PATH = "/reservations/{reservationId}/payment/status";
    public static final String WOMPI_WEBHOOK_PATH = "/payments/wompi/webhook";
    public static final String AGENCIES_ME_RESERVATIONS_PATH = "/agencies/me/reservations";
    public static final String AGENCIES_ME_RESERVATIONS_ID_PATH = "/agencies/me/reservations/{reservationId}";
    public static final String AGENCIES_ME_RESERVATIONS_STATUS_PATH = "/agencies/me/reservations/{reservationId}/status";
    public static final String AGENCIES_ME_RESERVATIONS_MESSAGES_PATH = "/agencies/me/reservations/{reservationId}/messages";
    public static final String AGENCIES_ID_RESERVATIONS_PATH = "/agencies/{agencyId}/reservations";
    public static final String AGENCIES_ID_RESERVATIONS_ID_PATH = "/agencies/{agencyId}/reservations/{reservationId}";
    public static final String AGENCIES_ID_RESERVATIONS_STATUS_PATH = "/agencies/{agencyId}/reservations/{reservationId}/status";
    public static final String AGENCIES_ID_RESERVATIONS_MESSAGES_PATH = "/agencies/{agencyId}/reservations/{reservationId}/messages";

    // Notifications
    public static final String NOTIFICATIONS_BASE_PATH = "/notifications";
    public static final String NOTIFICATIONS_STREAM_PATH = "/notifications/stream";
    public static final String NOTIFICATIONS_READ_ALL_PATH = "/notifications/read-all";
    public static final String NOTIFICATIONS_READ_PATH = "/notifications/{notificationId}/read";

    // Categories
    public static final String CATEGORIES_BASE_PATH = "/categories";
    public static final String CATEGORIES_ID_PATH = "/categories/{id}";

    // Agencies
    public static final String AGENCIES_BASE_PATH = "/agencies";
    public static final String AGENCIES_SEARCH_PATH = "/agencies/search";
    public static final String AGENCIES_USERS_PATH = "/agencies/users";
    public static final String AGENCIES_BY_USER_PATH = "/agencies/by-user";
    public static final String AGENCIES_MY_PATH = "/agencies/my";          // Agencias del usuario autenticado
    public static final String AGENCIES_DASHBOARD_PATH = "/agencies/dashboard";
    public static final String AGENCIES_PACKAGES_PATH = "/agencies/{id}/packages"; // Paquetes de una agencia
    public static final String AGENCIES_USERS_LIST_PATH = "/agencies/{id}/users";    // Listado de correos (Público)
    public static final String AGENCIES_USERS_MANAGE_PATH = "/agencies/{id}/users/{userId}"; // Editar/Eliminar (Protegido)

    // gecode
    public static final String GETCOORDINATES = "/tools/geocode";
    public static final String PLACES_NEARBY_PATH_PLACE = "/pruebas/places/nearby/getpalce";

    // Admin
    public static final String ADMIN_PLACES_VERIFY_PATH = "/places/{id}/verify";

    //Debug
    public static final String DEBUG = "/debug";
    public static final String DEBUG_EMAIL = "/debug/email";
    public static final String DEBUG_RECOVERY_EMAIL = "/debug/recovery-email";

    // Visits
    public static final String VISITS_CHECKIN_PATH         = "/pruebas/places/{id}/checkin";
    public static final String VISITS_CONFIRM_PATH         = "/pruebas/visits/{visitId}/confirm";
    public static final String ANALYTICS_TOP_PLACES_PATH   = "/pruebas/analytics/places/top";
    public static final String USER_TOP_VISITS_PATH        = "/pruebas/users/me/visits/top";
    public static final String USER_FAVORITES_PATH         = "/pruebas/users/me/favorites";
    public static final String USER_FAVORITE_BY_PLACE_PATH = "/pruebas/users/me/favorites/{placeId}";
}
