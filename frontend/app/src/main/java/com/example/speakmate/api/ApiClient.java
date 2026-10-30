package com.example.speakmate.api;

import com.example.speakmate.BuildConfig;
import com.example.speakmate.auth.AuthApi.ApiException;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import okhttp3.*;
import org.json.*;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;

/** Dùng chung HTTP cho tài khoản và học tập; chỉ gọi trên luồng nền. */
public final class ApiClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    // Không tự gửi lại POST khi mất mạng; màn hình phục hồi bằng UUID và dữ liệu server.
    private static final OkHttpClient SHARED_HTTP = new OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false)
            .retryOnConnectionFailure(false).build();

    private final Map<Integer, BackendHttpApi> services = new ConcurrentHashMap<>();

    private BackendHttpApi service(int timeoutMs) {
        return services.computeIfAbsent(timeoutMs, timeout -> {
            String base = BuildConfig.API_BASE_URL;
            if (!base.endsWith("/")) {
                base += "/";
            }
            OkHttpClient http = SHARED_HTTP.newBuilder().readTimeout(timeout, TimeUnit.MILLISECONDS)
                    .callTimeout(timeout + 10_000L, TimeUnit.MILLISECONDS).build();
            return new Retrofit.Builder().baseUrl(base + "api/v1/").client(http).build().create(BackendHttpApi.class);
        });
    }

    public JSONObject request(String method, String path, JSONObject body, String access, int timeoutMs)
            throws ApiException {
        try {
            URL url = new URL(BuildConfig.API_BASE_URL);
            if (!url.getProtocol().equals("https") && !(BuildConfig.DEBUG && url.getProtocol().equals("http"))) {
                throw new ApiException(0, "NETWORK_ERROR", "Ứng dụng chưa thể kết nối. Vui lòng thử lại.");
            }
            String authorization = access == null ? null : "Bearer " + access;
            BackendHttpApi api = service(timeoutMs);
            RequestBody payload = RequestBody.create(body == null ? "" : body.toString(), JSON);
            Call<ResponseBody> call;
            switch (method) {
            case "GET":
                call = api.get(path, authorization);
                break;
            case "POST":
                call = api.post(path, authorization, payload);
                break;
            case "PUT":
                call = api.put(path, authorization, payload);
                break;
            case "PATCH":
                call = api.patch(path, authorization, payload);
                break;
            default:
                throw new ApiException(0, "INVALID_REQUEST", "Không thể gửi yêu cầu này.");
            }
            Response<ResponseBody> result = call.execute();
            int status = result.code();
            JSONObject response;
            try (ResponseBody responseBody = result.isSuccessful() ? result.body() : result.errorBody()) {
                response = new JSONObject(read(responseBody == null ? null : responseBody.byteStream()));
            } catch (JSONException error) {
                throw new ApiException(status >= 400 ? status : 0, "INVALID_RESPONSE",
                        "Chưa thể đọc phản hồi. Hãy tải lại.");
            }
            if (status < 200 || status >= 300) {
                String code = response.optString("error", "REQUEST_FAILED");
                String message = response.optString("message", "Chưa thể xử lý yêu cầu.");
                if (status >= 500) {
                    message = code.equals("AI_TIMEOUT")
                            ? "Trợ lý phản hồi chậm. Nội dung đã gửi vẫn được giữ; hãy tải lại rồi thử tiếp."
                            : "Dịch vụ đang tạm thời không sẵn sàng. Vui lòng thử lại sau.";
                }
                throw new ApiException(status, code, message);
            }
            return response;
        } catch (InterruptedIOException error) {
            throw new ApiException(0, "NETWORK_TIMEOUT",
                    "Kết nối bị gián đoạn. Hãy tải lại để kiểm tra nội dung đã gửi.");
        } catch (IOException error) {
            throw new ApiException(0, "NETWORK_ERROR", "Không kết nối được SpeakMate. Hãy kiểm tra mạng rồi thử lại.");
        } catch (IllegalArgumentException error) {
            throw new ApiException(0, "INVALID_REQUEST", "Cấu hình kết nối chưa hợp lệ. Vui lòng thử lại sau.");
        }
    }

    private String read(InputStream input) throws IOException {
        if (input == null) {
            return "{}";
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (bytes.size() + count > 2_000_000) {
                throw new IOException("Response too large");
            }
            bytes.write(buffer, 0, count);
        }
        return bytes.toString(StandardCharsets.UTF_8.name());
    }
}
