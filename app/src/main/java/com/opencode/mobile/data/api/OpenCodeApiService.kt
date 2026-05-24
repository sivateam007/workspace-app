package com.opencode.mobile.data.api

import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

interface OpenCodeApiService {

    @POST("v1/chat/completions")
    suspend fun chatCompletion(
        @Header("Authorization") authHeader: String,
        @Body request: ChatRequest
    ): Response<ChatResponse>

    @GET("v1/models")
    suspend fun listModels(
        @Header("Authorization") authHeader: String
    ): Response<ModelsResponse>

    companion object {
        private const val BASE_URL = "https://opencode.ai/zen/"

        private val client = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        private val gson = GsonBuilder()
            .setLenient()
            .create()

        fun create(): OpenCodeApiService {
            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build()
            return retrofit.create(OpenCodeApiService::class.java)
        }
    }
}
