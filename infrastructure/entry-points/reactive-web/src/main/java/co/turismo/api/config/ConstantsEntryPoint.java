package co.turismo.api.config;

public class ConstantsEntryPoint {
    private ConstantsEntryPoint() {}

    public static final String API_BASE_PATH = "/api";
    public static final String API_ADMIN = "/admin";

    // Auth endpoints
    public static final String AUTH_REQUEST_CODE_PATH = "/auth/request-code";
    public static final String AUTH_VERIFY_CODE_PATH  = "/auth/verify-code";

    // Users
    public static final String CREATEUSER = "/create/user";

    // Places
    public static final String PLACES_BASE_PATH   = "/places";
    public static final String PLACES_NEARBY_PATH = "/places/nearby";
}
