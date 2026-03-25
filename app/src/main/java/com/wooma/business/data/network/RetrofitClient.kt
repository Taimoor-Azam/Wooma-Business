package com.wooma.business.data.network

import android.app.Activity
import android.app.ProgressDialog
import android.content.Context
import android.widget.Toast
import com.google.gson.Gson
import com.wooma.business.model.ErrorResponse
import com.wooma.business.storage.Prefs
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    private var retrofit: Retrofit? = null

    fun getClient(baseUrl: String, context: Context): Retrofit {
        if (retrofit == null) {

            val authInterceptor = Interceptor { chain ->
                val token = Prefs.getUser(context)?.access_token
//                val request = chain.request()
                val request = if (!token.isNullOrEmpty()) {
                    chain.request().newBuilder()
                        .addHeader("Authorization", "Bearer $token")
                        .build()
                } else {
                    chain.request()
                }

                chain.proceed(request)
            }

            val interceptor = HttpLoggingInterceptor()
            interceptor.level = HttpLoggingInterceptor.Level.BODY

            val client = OkHttpClient.Builder()
                .addInterceptor(authInterceptor)
                .addInterceptor(interceptor)
                .build()

            retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)
                .build()
        }
        return retrofit!!
    }

    fun getApi(context: Context): MyApi {
        return getClient(ApiClient.BASE_URL, context).create(MyApi::class.java)
    }
}

interface ApiResponseListener<T> {
    fun onSuccess(response: T)
    fun onFailure(errorMessage: ErrorResponse?)
    fun onError(throwable: Throwable)
}

fun <T, R> Activity.makeApiRequest(
    apiServiceClass: Class<T>,
    context: Activity,
    showLoading: Boolean = true,
    requestAction: (apiService: T) -> Call<R>,
    listener: ApiResponseListener<R>
) {
    val progressBar = ProgressDialog(context)
    if (showLoading) progressBar.show()

    progressBar.setMessage("Please Wait...")
    progressBar.setCancelable(false)

    val apiService = RetrofitClient.getClient(ApiClient.BASE_URL, context).create(apiServiceClass)
    val call = requestAction(apiService)

    call.enqueue(object : Callback<R> {
        override fun onResponse(call: Call<R>, response: Response<R>) {
            if (showLoading) progressBar.dismiss()

            if (response.isSuccessful) {
                response.body()?.let { body ->
                    listener.onSuccess(body)
                } ?: run {
                    var errorResponse: ErrorResponse?
                    try {
                        errorResponse = Gson().fromJson(
                            response.errorBody()?.string(),
                            ErrorResponse::class.java
                        )
                    } catch (e: Exception) {
                        errorResponse = null
                    }
                    listener.onFailure(errorResponse)
                }
            } else {
                var errorResponse: ErrorResponse?
                try {
                    errorResponse = Gson().fromJson(
                        response.errorBody()?.string(),
                        ErrorResponse::class.java
                    )
                } catch (e: Exception) {
                    errorResponse = null
                }
                listener.onFailure(errorResponse)

            }
        }

        override fun onFailure(call: Call<R>, t: Throwable) {
            if (showLoading) progressBar.dismiss()
            listener.onError(t)
            showToast("Network error occurred. Please try again.")
        }
    })
}

fun Context.showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, message, duration).show()
}