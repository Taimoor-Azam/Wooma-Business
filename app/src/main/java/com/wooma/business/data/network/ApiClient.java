package com.wooma.business.data.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {
    //    private static String BASE_URL = "https://cloudwapp.in/AH/motorcar/api/";
//   public static String BASE_URL = "https://motorcar.ae/";
    public static String BASE_URL = "https://api-dev-business.wooma.com";
//    public static String BASE_URL = "https://api.motorcar.ae/";

//    private static String BASE_URL = "http://79.143.178.173/motorcar/api/";
    private static String BASE_URL_GOOGLE = "https://maps.googleapis.com/";
    // public static String SITE_URL = "https://cloudwapp.in/AH/motorcar/";
//    public static String SITE_URL = "https://motorcar.ae/";
    public static String SITE_URL = BASE_URL;
//    public static String SITE_URL = "http://79.143.178.173/motorcar/";
    public static String COMMON_URL_FOR_WEB = BASE_URL;
//    public static String COMMON_URL_FOR_WEB = "https://motorcar.ae/";
    private static Retrofit retrofit = null;

    public static Retrofit getClient() {
        HttpLoggingInterceptor interceptor = new HttpLoggingInterceptor();
        interceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(interceptor)
                .readTimeout(60, TimeUnit.SECONDS)
                .connectTimeout(60, TimeUnit.SECONDS)
                .build();

        Gson gson = new GsonBuilder()
                .setLenient()
                .create();

        retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)
                .build();
        return retrofit;
    }

    public static Retrofit getClientGoole() {

        HttpLoggingInterceptor interceptor = new HttpLoggingInterceptor();
        interceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(interceptor)
                .connectTimeout(1000, TimeUnit.SECONDS)
                .readTimeout(1000, TimeUnit.SECONDS).build();

        retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL_GOOGLE)
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)
                .build();
        return retrofit;
    }
}
