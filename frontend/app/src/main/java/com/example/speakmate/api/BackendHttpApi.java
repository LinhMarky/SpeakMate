package com.example.speakmate.api;

import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.*;

/** Khai báo HTTP cho Retrofit; xử lý phiên và nghiệp vụ ở các lớp bên ngoài. */
public interface BackendHttpApi {

    @Streaming
    @Headers("Accept: application/json")
    @GET
    Call<ResponseBody> get(@Url String path, @Header("Authorization") String authorization);

    @Streaming
    @Headers("Accept: application/json")
    @POST
    Call<ResponseBody> post(@Url String path, @Header("Authorization") String authorization, @Body RequestBody body);

    @Streaming
    @Headers("Accept: application/json")
    @PATCH
    Call<ResponseBody> patch(@Url String path, @Header("Authorization") String authorization, @Body RequestBody body);
}
