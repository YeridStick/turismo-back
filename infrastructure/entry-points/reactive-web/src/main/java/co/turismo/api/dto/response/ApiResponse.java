package co.turismo.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(int status, String message, T data) {
    public static <T> ApiResponse<T> of(int status, String message, T data) { return new ApiResponse<>(status, message, data); }
    public static <T> ApiResponse<T> ok(T data)      { return of(200, "OK", data); }
    public static <T> ApiResponse<T> created(T data) { return of(201, "Created", data); }
    public static ApiResponse<Void> error(int status, String message) { return of(status, message, null); }
}
