package vn.tailinh.speakmate.dto.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RestResponse<T> {

    private int statusCode;

    private String error;

    private String message;

    private T data;

    public static <T> RestResponse<T> success(int statusCode, String message, T data) {
        RestResponse<T> response = new RestResponse<>();
        response.setStatusCode(statusCode);
        response.setMessage(message);
        response.setData(data);
        return response;
    }

    public static <T> RestResponse<T> failure(int statusCode, String error, String message) {
        RestResponse<T> response = new RestResponse<>();
        response.setStatusCode(statusCode);
        response.setError(error);
        response.setMessage(message);
        return response;
    }
}
