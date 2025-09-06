package co.turismo.api.config;

public class ConstantsEntryPoint {
    private ConstantsEntryPoint() {}

    public static final String API_BASE_PATH = "/api";
    public static final String API_ADMIN     = "/admin";

    // Auth
    public static final String AUTH_REQUEST_CODE_PATH = "/auth/request-code";
    public static final String AUTH_VERIFY_CODE_PATH  = "/auth/verify-code";

    // Users
    public static final String CREATEUSER = "/create/user";
    public static final String USERS_ME_PATH = "/users/me";

    // Places
    public static final String PLACES_BASE_PATH    = "/places";
    public static final String PLACES_NEARBY_PATH  = "/places/nearby";
    public static final String PLACES_MINE_PATH    = "/places/mine";
    public static final String PLACES_ACTIVE_PATH  = "/places/{id}/active";
    public static final String PLACES_OWNERS_PATH  = "/places/{id}/owners";
    public static final String PLACES_OWNER_DEL_PATH = "/places/{id}/owners/{email}";

    // Admin
    public static final String ADMIN_PLACES_VERIFY_PATH = "/places/{id}/verify";
}
