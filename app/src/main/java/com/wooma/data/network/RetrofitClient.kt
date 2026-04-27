package com.wooma.data.network

import android.app.Activity
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.wooma.activities.auth.GetStartedActivity
import com.google.gson.Gson
import com.wooma.activities.BaseActivity
import com.wooma.model.ErrorResponse
import com.wooma.storage.Prefs
import com.wooma.model.RefreshTokenRequest
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private var retrofit: Retrofit? = null

    fun getClient(baseUrl: String, context: Context): Retrofit {
        if (retrofit == null) {
            val interceptor = HttpLoggingInterceptor()
            interceptor.level = HttpLoggingInterceptor.Level.BODY

            val client = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val token = Prefs.getUser(context)?.access_token
                    val originalRequest = chain.request()
                    
                    val request = if (!token.isNullOrEmpty()) {
                        originalRequest.newBuilder()
                            .addHeader("Authorization", "Bearer $token")
                            .build()
                    } else {
                        originalRequest
                    }

                    val response = chain.proceed(request)

                    if (response.code == 401) {
                        val user = Prefs.getUser(context)
                        if (user != null && !user.refresh_token.isNullOrEmpty()) {
                            synchronized(this) {
                                val currentToken = Prefs.getUser(context)?.access_token
                                if (currentToken != token) {
                                    // Token already refreshed by another thread
                                    val newRequest = originalRequest.newBuilder()
                                        .header("Authorization", "Bearer $currentToken")
                                        .build()
                                    response.close()
                                    return@addInterceptor chain.proceed(newRequest)
                                }

                                // Perform synchronous refresh call
                                val refreshRequest =
                                    RefreshTokenRequest(user.refresh_token, user.access_token)
                                val api = Retrofit.Builder()
                                    .baseUrl(baseUrl)
                                    .addConverterFactory(GsonConverterFactory.create())
                                    .build()
                                    .create(MyApi::class.java)

                                try {
                                    val refreshResponse = api.refreshToken(refreshRequest).execute()
                                    if (refreshResponse.isSuccessful && refreshResponse.body() != null) {
                                        val newData = refreshResponse.body()!!.data
                                        user.access_token = newData.accessToken
                                        user.refresh_token = newData.refreshToken
                                        Prefs.saveUser(context, user)

                                        val newRequest = originalRequest.newBuilder()
                                            .header("Authorization", "Bearer ${newData.accessToken}")
                                            .build()
                                        response.close()
                                        return@addInterceptor chain.proceed(newRequest)
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    }
                    response
                }
                .addInterceptor(interceptor)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()

            val gson = com.google.gson.GsonBuilder()
                .serializeNulls()
                .create()

            retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create(gson))
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
    if (showLoading && !context.isFinishing && !context.isDestroyed) {
        progressBar.show()
        (context as? BaseActivity)?.activeProgressDialog = progressBar
    }

    progressBar.setMessage("Please Wait...")
    progressBar.setCancelable(false)

    val apiService = RetrofitClient.getClient(ApiClient.BASE_URL, context).create(apiServiceClass)
    val call = requestAction(apiService)

    call.enqueue(object : Callback<R> {
        override fun onResponse(call: Call<R>, response: Response<R>) {
            if (showLoading && progressBar.isShowing) progressBar.dismiss()

            if (response.code() == 401) {
                // If it's still 401 after the interceptor's refresh attempt, logout
                Prefs.clearUser(context)
                context.startActivity(
                    Intent(context, GetStartedActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                )
                return
            }

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
            if (showLoading && progressBar.isShowing) progressBar.dismiss()
            listener.onError(t)
            showToast("Network error occurred. Please try again.")
        }
    })
}

fun Context.showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, message, duration).show()
}