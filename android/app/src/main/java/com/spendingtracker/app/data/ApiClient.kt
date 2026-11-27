package com.spendingtracker.app.data

import com.google.gson.Gson
import com.spendingtracker.app.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private val gson = Gson()

    fun create(tokenStore: TokenStore): SpendingApiService {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            // Multi-page PDF extraction via Gemini can take 60-120s
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val token = tokenStore.getToken()
                val reqBuilder = chain.request().newBuilder()
                if (!token.isNullOrBlank()) {
                    reqBuilder.addHeader("Authorization", "Bearer $token")
                }
                chain.proceed(reqBuilder.build())
            }
            .addInterceptor(logging)
            .build()

        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(SpendingApiService::class.java)
    }

    fun parseErrorMessage(response: Response<*>): String {
        val raw = response.errorBody()?.string().orEmpty()
        return try {
            val envelope = gson.fromJson(raw, ApiErrorEnvelope::class.java)
            envelope?.error?.message ?: "Request failed (${response.code()})"
        } catch (_: Exception) {
            if (raw.isNotBlank()) raw else "Request failed (${response.code()})"
        }
    }
}
